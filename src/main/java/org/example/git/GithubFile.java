package org.example.git;

public class GithubFile
{

    private String owner;
    private String repo;
    private String path;
    private String name;
    private String commitHash;
    private String encodedBase64Content;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    public String getEncodedBase64Content() {
        return encodedBase64Content;
    }

    public void setEncodedBase64Content(String encodedBase64Content) {
        this.encodedBase64Content = encodedBase64Content;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getRepo() {
        return repo;
    }

    public void setRepo(String repo) {
        this.repo = repo;
    }
}
