package br.com.election;

import org.springframework.boot.SpringApplication;

public class TestElectionApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(ElectionApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
