package org.example.Exception;

public class BranchCreationException extends GithubException{
    public BranchCreationException(Throwable e) {
        super("Error while creating branch.  " + e.getMessage(), e);
    }
}
