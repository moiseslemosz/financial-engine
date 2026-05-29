package com.motorfinanceiro.config;
 
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
 
/**
 * Configuração da camada cognitiva — Spring AI + Gemini.
 *
 * O ChatClient é o ponto de entrada único para todas as interações
 * com o modelo de linguagem. Injetado nos services de IA via construtor.
 */
@Configuration
public class AiConfig {
 
    /**
     * ChatClient com configurações padrão definidas no application.properties:
     * - Modelo: gemini-1.5-flash
     * - Temperatura: 0.3 (respostas consistentes, menos aleatórias)
     *
     * O builder é auto-configurado pelo Spring AI starter quando a
     * propriedade spring.ai.google.genai.api-key está presente.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}