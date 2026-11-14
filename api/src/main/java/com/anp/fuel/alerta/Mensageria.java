package com.anp.fuel.alerta;

/** Porta de saída para a fila: desacopla a regra de negócio do broker (RabbitMQ). */
public interface Mensageria {
    void publicarCarga(EventoCargaConcluida evento);
    void publicarAlerta(Alerta alerta);
}
