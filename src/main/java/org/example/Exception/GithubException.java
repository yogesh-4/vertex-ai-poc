package org.example.Exception;

public class GithubException extends Exception {

    public GithubException(String message){
        super(message);
    }

    public GithubException(String message, Throwable e){
        super(message, e);
    }

}
