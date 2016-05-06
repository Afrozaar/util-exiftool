package com.afrozaar.util.exiftool;

import com.afrozaar.util.exiftool.AbstractJsonResponseConsumer.Builder;

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.im4java.core.ETOperation;
import org.im4java.core.ETOps;
import org.im4java.core.ExiftoolCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.ImageCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ExifTool implements IExifTool {

    private static final Logger LOG = LoggerFactory.getLogger(ExifTool.class);
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public JsonNode getTags(final String... fileLocations) throws ExiftoolException {
        final ETOps exifOp = new ETOperation().json().groupHeadings("");

        final List<String> usableLocations = Arrays.stream(fileLocations)
                .filter(location -> !location.isEmpty()).collect(Collectors.toList());

        LOG.debug("Retrieving tags for {} files", usableLocations.size());
        usableLocations.forEach(exifOp::addImage);

        Builder consumerBuilder = Builder.using(objectMapper);

        final JsonResponseConsumer outputConsumer = consumerBuilder.newConsumer();
        final JsonErrorResponseConsumer errorConsumer = consumerBuilder.newErrorConsumer();

        try {
            final ImageCommand command = new ExiftoolCmd();
            command.setOutputConsumer(outputConsumer);
            command.setErrorConsumer(errorConsumer);
            LOG.trace("Running exif ops: {}", exifOp);
            command.run(exifOp);

            final Optional<JsonNode> jsonNode = outputConsumer.getNode();
            if (jsonNode.isPresent()) {
                return jsonNode.get();
            } else {
                LOG.warn("No output after successful processing of exifOp '{}'", exifOp);
            }
        } catch (IOException | InterruptedException | IM4JavaException e) {
            throw new ExiftoolException(e);
        }

        final Optional<JsonNode> errorNode = errorConsumer.getNode();
        if (errorNode.isPresent()) {
            return errorNode.get();
        } else {
            throw new RuntimeException("Unexpected state. No output or error node available.");
        }
    }

    @Override
    public JsonNode fromResponse(String json) throws ExiftoolException {
        return fromResponse(new ByteArrayInputStream(json.getBytes()));
    }

    @Override
    public JsonNode fromResponse(InputStream inputStream) throws ExiftoolException {
        try {
            final JsonResponseConsumer consumer = Builder.using(objectMapper).newConsumer();
            consumer.consumeOutput(inputStream);

            final Optional<JsonNode> node = consumer.getNode();
            if (node.isPresent()) {
                return node.get();
            } else {
                throw new RuntimeException("No node extracted from supplied response");
            }
        } catch (IOException e) {
            throw new ExiftoolException(e);
        }
    }

    @Override
    public Set<String> getProfiles(ObjectNode node) {
        final Set<String> profiles = Sets.newHashSet(node.fieldNames());
        profiles.removeAll(Arrays.asList("SourceFile", "ExifTool"));
        return ImmutableSet.copyOf(profiles);
    }

    @Override
    public Set<Profile> getParsedProfiles(ObjectNode node) {

        Set<Profile> profiles = Sets.newLinkedHashSet();

        Profiles.supportedProfiles().forEach(profile -> {
            final Profile.Builder builder = Profile.Builder.aProfile(profile.name);
            final BiMap<SupportedTag, String> biMap = ImmutableBiMap.copyOf(profile.tagMap);
            final Map<String, Object> entriesForProfile = getEntriesForProfile(node, KnownProfile.valueOf(profile.name));

            final Consumer<Map.Entry<String, SupportedTag>> entryConsumer = supportedEntry -> {
                final Optional<Object> o = Optional.ofNullable(entriesForProfile.get(supportedEntry.getKey()));
                if (o.isPresent()) {
                    builder.withTag(supportedEntry.getValue(), o.get().toString());
                }
            };
            final Predicate<Map.Entry<String, SupportedTag>> entryPredicate = supportedEntry -> entriesForProfile.containsKey(supportedEntry.getKey());

            biMap.inverse().entrySet().stream()
                    .filter(entryPredicate)
                    .forEach(entryConsumer);

            final Profile build = builder.build();
            if (!build.tagMap.isEmpty()) {
                profiles.add(build);
            }

            if (LOG.isDebugEnabled()) {
                final Set<String> keys = entriesForProfile.keySet();
                final List<String> sortedValues = new ArrayList<>(profile.tagMap.values());
                sortedValues.sort(String.CASE_INSENSITIVE_ORDER);
                LOG.debug("-- Profile: {}", profile.name);
                LOG.debug("supports : {}", sortedValues);
                LOG.debug("has keys : {}", keys);
                LOG.debug("matched  : {}", build.tagMap.keySet());
                LOG.debug("----------------");
            }
        });

        return profiles;
    }

    @Override
    public Set<String> getSupportedProfiles() {
        return Arrays.stream(KnownProfile.values())
                .map(KnownProfile::name)
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, Object> getEntriesForProfile(ObjectNode node, KnownProfile profile) {
        final JsonNode profileNode = node.path(profile.name());
        final ImmutableMap.Builder<String, Object> map = ImmutableMap.builder();

        Function<JsonNode, Object> objectOrString = n -> {
            if (n.isTextual()) {
                return n.asText();
            } else if (n.isNumber()) {
                return n.numberValue();
            } else {
                LOG.warn("Unexpected type for node, returning as text: {}", n.asText());
                return n.asText();
            }
        };

        profileNode.fields().forEachRemaining(entry -> map.put(entry.getKey(), objectOrString.apply(entry.getValue())));
        return map.build();
    }

    @Override
    public ObjectNode getObjectNode(JsonNode node, int index) {
        return (ObjectNode) (node.isArray() ? node.get(index) : node);
    }

    @Override
    public Map<SupportedTag, Collection<Object>> getValuesForSupportedTags(ObjectNode node, SupportedTag... tags) {

        final Multimap<SupportedTag, Object> toReturn = ArrayListMultimap.create();
        final Consumer<Map.Entry<SupportedTag, String>> entryConsumer = entry -> toReturn.put(entry.getKey(), entry.getValue());

        getParsedProfiles(node).stream()
                .flatMap(profile -> profile.tagMap.entrySet().stream())
                .filter(entry -> !Strings.isNullOrEmpty(entry.getValue()))
                .forEach(entryConsumer);

        return toReturn.asMap();
    }

    @Override
    public JsonNode setTags(final String fileLocation, Map<SupportedTag, Object> tagMap) throws ExiftoolException {

        final ETOps ops = new ETOperation();

        final List<String> profileTags = Profiles.getProfileTagStringsForRequestedTags(tagMap);

        ((ETOperation) ops).setTags(profileTags.toArray(new String[profileTags.size()]));
        ops.addImage(ETOperation.IMG_PLACEHOLDER);

        LOG.trace("set tags on '{}' ops: {}", fileLocation, ops);

        final ImageCommand command = new ExiftoolCmd();

        try {
            command.run(ops, fileLocation);
            // read tags again and return latest data
            return getTags(fileLocation);
        } catch (IOException | InterruptedException | IM4JavaException e) {
            LOG.error("Error setting tags on {}", fileLocation, e);
            throw new RuntimeException("Can not set tags on " + fileLocation, e);
        }

    }

    /**
     * <pre>
     *     ObjectNode node = ...;
     *
     *     Map<String, String[]> profileKeysMap = ImmutableMap.of(
     *       "EXIF", new String[] { "ImageDescription" },
     *       "File", new String[] { "Comment" },
     *       "XMP", new String[] { "Description", "Title" },
     *       "IPTC", new String[] { "Caption-Abstract", "Object-Name" }
     *     );
     *
     *     Optional<String> result = exifTool.getFirstValueFromSpecMapping(node, profileKeysMap);
     * </pre>
     */
    @Override
    public Optional<String> getFirstValueFromSpecMapping(ObjectNode json, Map<String, String[]> profileKeysMap) {
        final Optional<String> first = profileKeysMap.entrySet().stream()
                .map(entry -> {
                    final Optional<JsonNode> profileNode = Optional.ofNullable(json.get(entry.getKey()));
                    return profileNode.isPresent() ? getFirstFieldValue(profileNode.get(), entry.getValue()) : Optional.<String>empty();
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();

        return first.isPresent() ? Optional.of(first.get()) : Optional.empty();
    }

    private Optional<String> getFirstFieldValue(JsonNode profileNode, String... fieldKeys) {
        final Optional<JsonNode> first = Arrays.stream(fieldKeys)
                .map(fieldKey -> Optional.ofNullable(profileNode.get(fieldKey)))
                .filter(Optional::isPresent)
                .filter(node -> !node.get().asText().equals(""))
                .map(Optional::get)
                .findFirst();

        return first.isPresent() ? Optional.of(first.get().asText()) : Optional.empty();
    }
}
