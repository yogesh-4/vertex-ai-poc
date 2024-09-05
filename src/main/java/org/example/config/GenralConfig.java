package org.example.config;

import org.example.git.GithubHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GenralConfig {

    @Value("${github.hostname}")
    private String hostName;

    @Value("${github.access.token}")
    private String accessToken;

    @Bean("sourceGitHubHelper")
    public GithubHelper githubHelper(){
        return new GithubHelper(accessToken, hostName);
    }

    @Bean("targetGitHelper")
    public GithubHelper targetgithubHelper(){
        return new GithubHelper(accessToken, hostName);
    }
}
