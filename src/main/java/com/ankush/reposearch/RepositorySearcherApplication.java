package com.ankush.reposearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RepositorySearcherApplication {
  public static void main(String[] args) {
    SpringApplication.run(RepositorySearcherApplication.class, args);
  }
}
