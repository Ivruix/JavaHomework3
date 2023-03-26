package org.example;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

public class StorageAgent extends Agent {
    private final Map<Integer, Double> storage = new HashMap<>();

    protected void setup() {
        // Регистрация в DF
        addBehaviour(new RegisterInDFBehaviour(this, "storage"));

        // Запись создания агента в логи
        Utils.logAgent(this, "created");

        // Чтение списка продуктов
        JSONParser parser = new JSONParser();
        try (Reader reader = new FileReader(".\\input\\products.txt")) {
            JSONObject jsonObject = (JSONObject) parser.parse(reader);
            JSONArray products = (JSONArray) jsonObject.get("products");
            for (Object o : products) {
                JSONObject product = (JSONObject) o;
                Integer type = ((Number) product.get("prod_item_type")).intValue();
                Double quantity = ((Number) product.get("prod_item_quantity")).doubleValue();
                storage.put(type, quantity);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Запуск поведения получения сообщений
        addBehaviour(new MessageReaderBehaviour());
    }

    protected void takeDown() {
        Utils.logAgent(this, "terminated");
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    private class MessageReaderBehaviour extends CyclicBehaviour {
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                if (msg.getPerformative() == ACLMessage.REQUEST) {
                    String[] tokens = msg.getContent().split(" ");
                    int productId = Integer.parseInt(tokens[0]);
                    double amount = Double.parseDouble(tokens[1]);

                    ACLMessage reply = msg.createReply();

                    if (storage.get(productId) >= amount) {
                        reply.setPerformative(ACLMessage.AGREE);
                        storage.put(productId, storage.get(productId) - amount);
                    } else {
                        reply.setPerformative(ACLMessage.REFUSE);
                    }

                    send(reply);
                }
            } else {
                block();
            }
        }
    }
}