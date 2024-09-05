package org.example.Exception;

public class MergeException extends GithubException{
    public MergeException(int number, Throwable e) {
        super("Error while merging PR : " + number + " " + e.getMessage(), e);
    }

    public MergeException(String errorMessage) {
        super("There are conflicts between main and release branch.Please "+errorMessage);
    }
}
