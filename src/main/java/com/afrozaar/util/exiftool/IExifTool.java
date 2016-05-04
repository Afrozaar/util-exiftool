package com.afrozaar.util.exiftool;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
@Service
public interface IExifTool {
    JsonNode getTags(String... fileLocations) throws ExiftoolException;

    JsonNode fromResponse(String json) throws ExiftoolException;
    JsonNode fromResponse(InputStream inputStream) throws ExiftoolException;

    Set<String> getProfiles(ObjectNode node);

    Set<Profile> getParsedProfiles(ObjectNode node);

    Set<String> getSupportedProfiles();

    Map<String, Object> getEntriesForProfile(ObjectNode node, KnownProfile profile);

    ObjectNode getObjectNode(JsonNode node, int index);

    Map<SupportedTag, Collection<Object>> getValuesForSupportedTags(ObjectNode node, SupportedTag ... tags);

    Optional<String> getFirstValueFromSpecMapping(ObjectNode json, Map<String, String[]> profileKeysMap);

    JsonNode setTags(String fileLocation, Map<SupportedTag, Object> tagMap) throws ExiftoolException;
}
