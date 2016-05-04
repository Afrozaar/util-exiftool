package com.afrozaar.util.exiftool;

import static java.lang.String.format;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

public class Profile {

    final String name;
    final Map<SupportedTag, String> tagMap;

    private Profile(String name, Map<SupportedTag, String> tagMap) {
        this.name = name;
        this.tagMap = tagMap;
    }

    public Optional<String> getTagString(SupportedTag supportedTag) {
        return tagMap.containsKey(supportedTag) ? Optional.of(format("%s:%s", name, tagMap.get(supportedTag))) : Optional.empty();
    }

    /*
    Having thoughts of using BiMaps here to look up the SupportedTag from a String value if found.
     */
    public boolean supportsTag(String supportedTagString) {
        try {
            return tagMap.containsKey(SupportedTag.valueOf(supportedTagString));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("Profile{");
        sb.append("name='").append(name).append('\'');
        sb.append(", tagMapKeys=").append(tagMap.keySet());
        sb.append('}');
        return sb.toString();
    }

    public static class Builder {
        String name;

        ImmutableMap.Builder<SupportedTag, String> builder = ImmutableMap.builder();

        private Builder() {
        }

        public static Builder aProfile(String name) {
            return new Builder().withName(name);
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withTag(SupportedTag tag, String representation) {
            builder.put(tag, representation);
            return this;
        }

        public Profile build() {
            return new Profile(name, builder.build());
        }
    }
}
