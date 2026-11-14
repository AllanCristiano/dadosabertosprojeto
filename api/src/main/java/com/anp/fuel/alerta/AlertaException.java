package com.anp.fuel.alerta;

/** Violações de regra de negócio do módulo de alertas; o handler HTTP as traduz em 404/409/422. */
public abstract class AlertaException extends RuntimeException {
    protected AlertaException(String message) { super(message); }

    public static class NaoEncontrada extends AlertaException {
        public NaoEncontrada(long id) { super("Regra " + id + " não encontrada"); }
    }

    public static class Invalida extends AlertaException {
        public Invalida(String message) { super(message); }
    }

    public static class Duplicada extends AlertaException {
        public Duplicada(String produto, String uf) {
            super("Já existe regra ativa para " + produto + " em " + uf);
        }
    }
}
