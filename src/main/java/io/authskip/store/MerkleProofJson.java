package io.authskip.store;

import io.authskip.MerkleProof;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MerkleProofJson {
    private static final Pattern FIELD_PATTERN = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern SIBLINGS_ARRAY_PATTERN = Pattern.compile(
            "\"siblings\"\\s*:\\s*\\[(.*)]", Pattern.DOTALL);
    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{([^{}]*)}");

    private MerkleProofJson() {
    }

    static String toJson(MerkleProof proof) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        appendField(json, "leaf_value", proof.leafValue());
        json.append(',');
        appendField(json, "root_hash", proof.rootHash());
        json.append(",\"siblings\":[");
        for (int i = 0; i < proof.siblings().size(); i++) {
            MerkleProof.Sibling sibling = proof.siblings().get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append('{');
            appendField(json, "position", sibling.position().name());
            json.append(',');
            appendField(json, "hash", sibling.hash());
            json.append('}');
        }
        json.append("]}");
        return json.toString();
    }

    static MerkleProof fromJson(String json) {
        String leafValue = field(json, "leaf_value");
        String rootHash = field(json, "root_hash");
        List<MerkleProof.Sibling> siblings = new ArrayList<>();
        Matcher arrayMatcher = SIBLINGS_ARRAY_PATTERN.matcher(json);
        if (!arrayMatcher.find()) {
            throw new IllegalArgumentException("Missing siblings array in proof JSON");
        }
        Matcher matcher = OBJECT_PATTERN.matcher(arrayMatcher.group(1));
        while (matcher.find()) {
            String object = matcher.group(1);
            siblings.add(new MerkleProof.Sibling(
                    MerkleProof.Position.valueOf(field(object, "position")),
                    field(object, "hash")));
        }
        return new MerkleProof(leafValue, List.copyOf(siblings), rootHash);
    }

    static String leafHash(MerkleProof proof) {
        return proof.leafHash();
    }

    private static void appendField(StringBuilder json, String name, String value) {
        json.append('"').append(name).append("\":\"").append(escape(value)).append('"');
    }

    private static String field(String json, String name) {
        Matcher matcher = Pattern.compile(FIELD_PATTERN.pattern().formatted(name)).matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing field in proof JSON: " + name);
        }
        return unescape(matcher.group(1));
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static String unescape(String value) {
        StringBuilder unescaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch != '\\' || i + 1 >= value.length()) {
                unescaped.append(ch);
                continue;
            }

            char next = value.charAt(++i);
            switch (next) {
                case '"' -> unescaped.append('"');
                case '\\' -> unescaped.append('\\');
                case 'b' -> unescaped.append('\b');
                case 'f' -> unescaped.append('\f');
                case 'n' -> unescaped.append('\n');
                case 'r' -> unescaped.append('\r');
                case 't' -> unescaped.append('\t');
                case 'u' -> {
                    if (i + 4 >= value.length()) {
                        throw new IllegalArgumentException("Invalid unicode escape in proof JSON");
                    }
                    String hex = value.substring(i + 1, i + 5);
                    unescaped.append((char) Integer.parseInt(hex, 16));
                    i += 4;
                }
                default -> unescaped.append(next);
            }
        }
        return unescaped.toString();
    }
}
