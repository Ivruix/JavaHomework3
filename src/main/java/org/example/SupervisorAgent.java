package org.example;

import jade.core.*;
import jade.core.Runtime;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.wrapper.ContainerController;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.FileReader;
import java.io.Reader;
import java.time.LocalDateTime;
import java.util.*;

public class SupervisorAgent extends Agent {
    private ContainerController containerController;
    private final Map<String, Integer> nicknameCounter = new HashMap<>();

    protected void setup() {
        // Создание контейнера
        ProfileImpl profile = new ProfileImpl();
        profile.setParameter(Profile.CONTAINER_NAME, "Restaurant-Agents");
        containerController = Runtime.instance().createAgentContainer(profile);

        // Регистрация в DF
        addBehaviour(new RegisterInDFBehaviour(this, "supervisor"));

        // Сброс логов
        Utils.resetLogs();

        // Запись создания агента в логи
        Utils.logAgent(this, "created");

        // Фиксирование времени запуск задачи для конвертации реального времени во время симуляции
        Utils.programStart = LocalDateTime.now();

        // Запуск агентов
        launchAgents();

        // Запуск поведения получения сообщений
        addBehaviour(new MessageReaderBehaviour());
    }

    protected void takeDown() {
        // Запись удаления агента в логи
        Utils.logAgent(this, "terminated");

        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    private String getNextNickname(String nickname) {
        Integer nextNumber = nicknameCounter.merge(nickname, 1, Integer::sum);
        if (nextNumber == 1) {
            return nickname;
        }
        return nickname + "-" + nextNumber;
    }

    private void launchAgent(String nickname, String className, Object[] args) {
        Utils.launchAgent(containerController, getNextNickname(nickname), className, args);
    }

    private void launchAgents() {
        launchAgent("storage", "org.example.StorageAgent", null);
        launchCooks();
        launchEquipment();
        launchVisitors();
    }

    private void launchCooks() {
        JSONParser parser = new JSONParser();

        try (Reader reader = new FileReader(".\\input\\cooks.txt")) {
            JSONObject jsonObject = (JSONObject) parser.parse(reader);
            JSONArray cooks = (JSONArray) jsonObject.get("cooks");

            for (Object o : cooks) {
                JSONObject cook = (JSONObject) o;

                if ((Boolean) cook.get("cook_active")) {
                    int id = ((Number) cook.get("cook_id")).intValue();
                    launchAgent((String) cook.get("cook_name"), "org.example.CookAgent", new Object[] {id});
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void launchEquipment() {
        JSONParser parser = new JSONParser();

        try (Reader reader = new FileReader(".\\input\\equipment.txt")) {
            JSONObject jsonObject = (JSONObject) parser.parse(reader);
            JSONArray equipmentArray = (JSONArray) jsonObject.get("equipment");

            for (Object o : equipmentArray) {
                JSONObject equipment = (JSONObject) o;

                if ((Boolean) equipment.get("equip_active")) {
                    int type = ((Number) equipment.get("equip_type")).intValue();
                    int id = ((Number) equipment.get("equip_id")).intValue();
                    launchAgent((String) equipment.get("equip_name"), "org.example.EquipmentAgent",
                            new Object[] {type, id});
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void launchVisitors() {
        JSONParser parser = new JSONParser();

        try (Reader reader = new FileReader(".\\input\\visitors_orders.txt")) {
            JSONObject jsonObject = (JSONObject) parser.parse(reader);
            JSONArray orders = (JSONArray) jsonObject.get("visitors_orders");

            for (Object o : orders) {
                JSONObject order = (JSONObject) o;
                Object[] args = new Object[4];

                args[0] = order.get("vis_name");
                args[1] = order.get("vis_ord_started");
                args[2] = ((Number) order.get("vis_ord_total")).intValue();
                List<Integer> dishList = new ArrayList<>();
                JSONArray dishes = (JSONArray) order.get("vis_ord_dishes");
                for (Object dish : dishes) {
                    dishList.add(((Number) ((JSONObject) dish).get("menu_dish")).intValue());
                }
                args[3] = dishList;

                launchAgent((String) args[0], "org.example.VisitorAgent", args);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class MessageReaderBehaviour extends CyclicBehaviour {
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                if (msg.getPerformative() == ACLMessage.REQUEST) {
                    String[] tokens = msg.getContent().split(" ");
                    if (Objects.equals(tokens[0], "process")) {
                        Object[] args = new Object[2];
                        args[0] = msg.getSender();
                        args[1] = Integer.parseInt(tokens[1]);
                        launchAgent("process", "org.example.ProcessAgent", args);
                    } else if (Objects.equals(tokens[0], "operation")) {
                        Object[] args = new Object[6];
                        args[0] = msg.getSender();

                        // dishCard
                        args[1] = Integer.parseInt(tokens[1]);

                        // type
                        args[2] = Integer.parseInt(tokens[2]);

                        // equipment
                        args[3] = Integer.parseInt(tokens[3]);

                        // time
                        args[4] = Double.parseDouble(tokens[4]);

                        // products
                        List<Pair<Integer, Double>> products = new ArrayList<>();
                        for (int i = 5; i < tokens.length; i += 2) {
                            products.add(new Pair<>(Integer.parseInt(tokens[i]), Double.parseDouble(tokens[i + 1])));
                        }
                        args[5] = products;

                        launchAgent("operation", "org.example.OperationAgent", args);
                    }
                }
            } else {
                block();
            }
        }
    }
}
