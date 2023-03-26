package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import jade.core.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

// Класс вспомогательных функций
public class Utils {

    // Запуск агента
    public static boolean launchAgent(ContainerController containerController,
                                      String nickname, String className, Object[] args) {
        try {
            AgentController agentController = containerController.createNewAgent(nickname, className, args);
            agentController.start();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    // Получение списка агентов по типу
    public static AID[] getAgentsByType(Agent agent, String serviceType) {
        AID[] foundAgents = null;

        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType);
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.addServices(sd);

        try {
            DFAgentDescription[] result = DFService.search(agent, dfd);
            foundAgents = new AID[result.length];
            for (int i = 0; i < result.length; ++i) {
                foundAgents[i] = result[i].getName();
            }
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        return foundAgents;
    }

    // Получение даты начала симуляции
    public static LocalDateTime getSimulationStart() {
        return LocalDateTime.parse("2023-02-28T08:00:00");
    }

    // Получение скорости симуляции
    public static double getSimulationSpeed() {
        return 400.0;
    }

    // Получение технологической карты блюда
    public static int getDishCardByDishId(int dishId) {
        int dishCardId = -1;

        JSONParser parser = new JSONParser();

        try (Reader reader = new FileReader(".\\input\\menu_dishes.txt")) {
            JSONObject jsonObject = (JSONObject) parser.parse(reader);
            JSONArray menuDishes = (JSONArray) jsonObject.get("menu_dishes");
            for (Object o : menuDishes) {
                JSONObject menuDish = (JSONObject) o;
                if (((Number) menuDish.get("menu_dish_id")).intValue() == dishId) {
                    dishCardId = ((Number) menuDish.get("menu_dish_card")).intValue();
                    break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return dishCardId;
    }

    // Получение операций приготовляения блюда
    public static List<Operation> getDishOperations(int dishId) {
        int dishCardId = getDishCardByDishId(dishId);

        JSONParser parser = new JSONParser();

        List<Operation> result = new ArrayList<>();

        try (Reader reader = new FileReader(".\\input\\dish_cards.txt")) {
            JSONObject jsonObject = (JSONObject) parser.parse(reader);
            JSONArray dishCards = (JSONArray) jsonObject.get("dish_cards");

            for (Object o : dishCards) {
                JSONObject dishCard = (JSONObject) o;

                if (((Number) dishCard.get("card_id")).intValue() == dishCardId) {
                    JSONArray operations = (JSONArray) dishCard.get("operations");

                    for (Object operationObject : operations) {
                        JSONObject operation = (JSONObject) operationObject;

                        int type = ((Number) operation.get("oper_type")).intValue();
                        int equipment = ((Number) operation.get("equip_type")).intValue();
                        double time = ((Number) operation.get("oper_time")).doubleValue();
                        int asyncPoint = ((Number) operation.get("oper_async_point")).intValue();
                        List<Pair<Integer, Double>> productList = new ArrayList<>();

                        JSONArray products = (JSONArray) operation.get("oper_products");

                        for (Object productObject : products) {
                            JSONObject product = (JSONObject) productObject;

                            productList.add(new Pair<>(((Number) product.get("prod_type")).intValue(),
                                    ((Number) product.get("prod_quantity")).doubleValue()));

                        }

                        result.add(new Operation(type, equipment, time, asyncPoint, productList));
                    }

                    break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    // Получение приблизительного времени готовки блюда
    public static double estimateDishPreparationTime(int dishId) {
        double result = 0;

        List<Operation> operations = getDishOperations(dishId);
        List<List<Operation>> operationOrder = operationsToParallelOperationOrder(operations);

        for (int i = 0; i < operationOrder.size(); i++) {
            double max = 0;
            for (int j = 0; j < operationOrder.get(i).size(); j++) {
                if (max < operationOrder.get(i).get(j).time()) {
                    max = operationOrder.get(i).get(j).time();
                }
            }
            result += max;
        }

        return result;
    }

    // Преобразования списка операций в список параллельных операций
    public static List<List<Operation>> operationsToParallelOperationOrder(List<Operation> operations) {
        List<List<Operation>> operationOrder = new ArrayList<>();

        Set<Integer> processed = new HashSet<>();
        for (int i = 0; i < operations.size(); i++) {
            List<Operation> nextList = new ArrayList<>();

            if (processed.contains(operations.get(i).asyncPoint())) {
                continue;
            }

            if (operations.get(i).asyncPoint() == 0) {
                nextList.add(operations.get(i));
            } else {
                nextList.add(operations.get(i));
                int asyncPoint = operations.get(i).asyncPoint();
                for (int j = i + 1; j < operations.size(); j++) {
                    if (operations.get(j).asyncPoint() == asyncPoint) {
                        nextList.add(operations.get(j));
                    }
                }
                processed.add(asyncPoint);
            }
            operationOrder.add(nextList);
        }

        return operationOrder;
    }

    // Запись информации о событии агента
    public static void logAgent(Agent agent, String type) {
        JSONObject newAgentLog = new JSONObject();
        newAgentLog.put("type", type);
        newAgentLog.put("real_time", LocalDateTime.now().toString());
        newAgentLog.put("global_name", agent.getName());
        newAgentLog.put("class", agent.getClass().getName());

        JSONObject agentLog;

        synchronized (Utils.class) {
            try (Reader reader = new FileReader(".\\output\\agents_log.txt")) {
                JSONParser parser = new JSONParser();
                agentLog = (JSONObject) parser.parse(reader);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            ((JSONArray) agentLog.get("agents_log")).add(newAgentLog);

            try (Writer writer = new FileWriter(".\\output\\agents_log.txt")) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                JsonElement je = JsonParser.parseString(agentLog.toString());
                writer.write(gson.toJson(je));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Время начала программы
    public static LocalDateTime programStart;

    // Конвертация текущего времени во время симуляции
    public static LocalDateTime converToSimulationTime(LocalDateTime dateTime) {
        long millisSinceStart =
                (long) (ChronoUnit.MILLIS.between(programStart, dateTime) * getSimulationSpeed());
        return getSimulationStart().plus(millisSinceStart, ChronoUnit.MILLIS);
    }

    // Получение индекса по имени
    public static int getIndexFromName(String name) {
        if (name.contains("-")) {
            return Integer.parseInt(name.split("-")[1]);
        }
        return 1;
    }

    // Сброс логов
    public static void resetLogs() {
        try (Writer writer = new FileWriter(".\\output\\agents_log.txt")) {
            writer.write("{\"agents_log\":[]}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try (Writer writer = new FileWriter(".\\output\\operation_log.txt")) {
            writer.write("{\"operation_log\":[]}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try (Writer writer = new FileWriter(".\\output\\process_log.txt")) {
            writer.write("{\"process_log\":[]}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try (Writer writer = new FileWriter(".\\output\\visitors_orders.txt")) {
            writer.write("{\"visitors_orders\":[]}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
