package com.anp.fuel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Monolito modular: cada pacote (auth, preco, analise) é um módulo com controller/repository próprios. */
@SpringBootApplication
public class FuelApplication {
    public static void main(String[] args) { SpringApplication.run(FuelApplication.class, args); }
}
