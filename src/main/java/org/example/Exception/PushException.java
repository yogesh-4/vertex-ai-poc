package org.example.Exception;

public class PushException extends GithubException {
    public PushException(Throwable e) {
        super("Error while pushing files. " +  e.getMessage(), e);
    }
}
