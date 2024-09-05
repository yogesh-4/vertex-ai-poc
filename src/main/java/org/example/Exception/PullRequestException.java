package org.example.Exception;

public class PullRequestException extends GithubException{
    public PullRequestException(String message) {
        super("Error while creating pull request: " + message);
    }
}
