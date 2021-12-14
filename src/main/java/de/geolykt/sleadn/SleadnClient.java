package de.geolykt.sleadn;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.json.JSONObject;

public class SleadnClient {

    private static final class ExtensionDescriptorDeserializer implements Function<HttpResponse<String>, Optional<ExtensionDescriptor>> {

        private final SleadnClient client;

        public ExtensionDescriptorDeserializer(SleadnClient client) {
            this.client = client;
        }

        @Override
        public Optional<ExtensionDescriptor> apply(HttpResponse<String> response) {
            if (response.statusCode() > 399) {
                return Optional.empty();
            }
            if (response.headers().allValues("Content-Type").contains("application/json")) {
                return Optional.of(client.deserializeFullExtension(response.body()));
            } else {
                // Assume compromised server
                return Optional.empty();
            }
        }
    }

    private static final class ExtensionListDeserializer implements Function<HttpResponse<String>, Optional<List<ExtensionDescriptor>>> {

        private final SleadnClient client;

        public ExtensionListDeserializer(SleadnClient client) {
            this.client = client;
        }

        @Override
        public Optional<List<ExtensionDescriptor>> apply(HttpResponse<String> response) {
            if (response.statusCode() > 399) {
                return Optional.empty();
            }
            if (response.headers().allValues("Content-Type").contains("application/json")) {
                return Optional.of(client.deserializeList(response.body()));
            } else {
                // Assume compromised server
                return Optional.empty();
            }
        }
    }

    private final String sleadnAuthority;

    private final HttpClient httpClient;

    public SleadnClient(HttpClient client, String sleadnAuthority) {
        this.httpClient = client;
        this.sleadnAuthority = sleadnAuthority;
    }

    protected ExtensionDescriptor deserializeFullExtension(String string) {
        JSONObject extension = new JSONObject(string);
        String name = extension.getString("name");
        String identifier = extension.getString("identifier");
        String description = extension.getString("description");
        String license = extension.getString("license");
        String latestVersion = extension.getString("latestVersion");
        List<VersionDescriptor> versions = new ArrayList<>();
        List<String> authors = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        for (Object var0 : extension.getJSONArray("authors")) {
            authors.add(var0.toString());
        }
        for (Object var0 : extension.getJSONArray("tags")) {
            tags.add(var0.toString());
        }
        for (Object var0 : extension.getJSONArray("versions")) {
            if (var0 instanceof JSONObject) {
                JSONObject version = (JSONObject) var0;
                List<DependencyDescriptor> depends = new CopyOnWriteArrayList<>();
                for (Object var1 : version.getJSONArray("dependencies")) {
                    if (var1 instanceof JSONObject) {
                        JSONObject dependency = (JSONObject) var1;
                        String dependencyId = dependency.getString("id");
                        String dependencyVersion = dependency.getString("version");
                        boolean hardDepend = dependency.getBoolean("hard");
                        depends.add(new DependencyDescriptor(dependencyId, dependencyVersion, hardDepend));
                    } else {
                        System.err.println("Illegally formatted dependency.");
                    }
                }
                versions.add(new VersionDescriptor(version.getString("name"), version.getString("url"), depends));
            } else {
                System.err.println("Illegally formatted version.");
            }
        }
        return new ExtensionDescriptor(name, identifier, description, license, latestVersion, authors, tags, Optional.of(versions));
    }

    protected List<ExtensionDescriptor> deserializeList(String string) {
        List<ExtensionDescriptor> descriptors = new ArrayList<>();
        JSONObject listObject = new JSONObject(string);
        if (listObject.getInt("version") != 0) {
            System.err.println("Potentially outdated SlEADN client. Consider updating.");
        }
        for (Object o : listObject.getJSONArray("extensions")) {
            if (o instanceof JSONObject) {
                JSONObject extension = (JSONObject) o;
                String name = extension.getString("name");
                String identifier = extension.getString("identifier");
                String description = extension.getString("description");
                String license = extension.getString("license");
                String latestVersion = extension.getString("latestVersion");
                List<String> authors = new CopyOnWriteArrayList<>();
                List<String> tags = new CopyOnWriteArrayList<>();
                for (Object var0 : extension.getJSONArray("authors")) {
                    authors.add(var0.toString());
                }
                for (Object var0 : extension.getJSONArray("tags")) {
                    tags.add(var0.toString());
                }
                descriptors.add(new ExtensionDescriptor(name, identifier, description, license, latestVersion, authors, tags,
                        Optional.empty()));
            } else {
                System.err.println("Illegally formatted extension.");
            }
        }
        return descriptors;
    }

    public CompletableFuture<Optional<List<ExtensionDescriptor>>> getClientList() {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(sleadnAuthority + "list")).build();
        return httpClient.sendAsync(request, BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(new ExtensionListDeserializer(this));
    }

    public CompletableFuture<Optional<ExtensionDescriptor>> getExtensionMetadata(String extensionId) {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(sleadnAuthority + "get/" + extensionId)).build();
        return httpClient.sendAsync(request, BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(new ExtensionDescriptorDeserializer(this));
    }
}
