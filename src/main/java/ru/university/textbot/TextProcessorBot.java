package ru.university.textbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.university.textbot.config.BotConfig;
import ru.university.textbot.quiz.*;

import okhttp3.OkHttpClient;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TextProcessorBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger logger = LoggerFactory.getLogger(TextProcessorBot.class);
    private final TelegramClient telegramClient;
    private final SessionManager sessionManager;

    public TextProcessorBot() {
        // Настройка прокси
        java.net.Authenticator.setDefault(new java.net.Authenticator() {
            @Override
            protected java.net.PasswordAuthentication getPasswordAuthentication() {
                return new java.net.PasswordAuthentication("BQvnjM", "Az9dt8".toCharArray());
            }
        });

        System.setProperty("socksProxyHost", "68.209.62.102");
        System.setProperty("socksProxyPort", "8000");
        System.setProperty("java.net.socks.username", "BQvnjM");
        System.setProperty("java.net.socks.password", "Az9dt8");

        Proxy proxy = new Proxy(Proxy.Type.SOCKS,
                new InetSocketAddress("68.209.62.102", 8000));

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        this.telegramClient = new OkHttpTelegramClient(okHttpClient, BotConfig.BOT_TOKEN);

        List<QuizQuestion> questions = WordDataLoader.loadFromFile("/words.txt");
        this.sessionManager = new SessionManager(questions);

        logger.info("✅ Бот инициализирован (викторина: синонимы/антонимы)");
        logger.info("📚 Загружено вопросов: {}", questions.size());
        logger.info("🔧 Прокси: 68.209.62.102:8000 (SOCKS)");
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String userMessage = update.getMessage().getText().trim();
            long chatId = update.getMessage().getChatId();
            long userId = update.getMessage().getFrom().getId();
            String userName = update.getMessage().getFrom().getFirstName();

            String response;
            String parseMode = null;

            if (userMessage.equals("/start")) {
                response = getStartMessage(userName);
                parseMode = "Markdown";
            } else if (userMessage.equals("/quiz")) {
                response = startQuiz(userId, userName);
                parseMode = "Markdown";
            } else if (userMessage.equals("/stop")) {
                response = stopQuiz(userId);
                parseMode = "Markdown";
            } else if (userMessage.equals("/stats")) {
                response = sessionManager.getStats(userId, userName);
                parseMode = "Markdown";
            } else {
                response = handleAnswer(userId, userMessage);
                parseMode = "Markdown";
            }

            SendMessage reply = SendMessage.builder()
                    .chatId(chatId)
                    .text(response)
                    .parseMode(parseMode)
                    .build();

            try {
                telegramClient.execute(reply);
                logger.info("✅ Ответ отправлен пользователю {}", userName);
            } catch (TelegramApiException e) {
                logger.error("❌ Ошибка отправки: {}", e.getMessage());
            }
        }
    }

    private String getStartMessage(String userName) {
        return "🎮 *Привет, " + userName + "!*\n\n" +
                "Добро пожаловать в игру *«Синонимы и Антонимы»*!\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📖 *Что такое синонимы и антонимы?*\n\n" +
                "• *Синонимы* — это слова, которые имеют похожее или одинаковое значение.\n" +
                "  Пример: `хороший` ≈ `отличный`\n\n" +
                "• *Антонимы* — это слова с противоположным значением.\n" +
                "  Пример: `хороший` ≠ `плохой`\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "🎮 *Как играть:*\n\n" +
                "1. Напиши `/quiz`, чтобы начать викторину\n" +
                "2. Бот задает вопрос: нужен синоним или антоним к слову\n" +
                "3. За каждый правильный ответ дается 1 очко\n" +
                "4. Игра заканчивается, когда все вопросы заданы\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📋 *Доступные команды:*\n\n" +
                "• `/quiz` - начать новую викторину\n" +
                "• `/stop` - остановить текущую игру\n" +
                "• `/stats` - посмотреть статистику\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                "Удачи! 🍀";
    }

    private String startQuiz(long userId, String userName) {
        sessionManager.startQuiz(userId, userName);
        QuizGame game = sessionManager.getCurrentGame(userId);

        if (game == null) {
            return "❌ Не удалось начать игру. Попробуйте позже.";
        }

        QuizQuestion firstQuestion = game.getRandomQuestion();

        if (firstQuestion == null) {
            return "❌ Вопросы закончились! Напишите /stop и /quiz заново.";
        }

        return String.format(
                "🎮 *Игра началась!*\n\n" +
                        "Всего вопросов: %d\n\n" +
                        "🔤 %s\n\n" +
                        "Введи свой ответ 👇",
                game.getTotalQuestions(),
                firstQuestion.getQuestion()
        );
    }

    private String stopQuiz(long userId) {
        QuizGame game = sessionManager.getCurrentGame(userId);

        if (game == null) {
            return "❌ Нет активной игры.\nНапишите /quiz, чтобы начать новую!";
        }

        int score = game.getScore();
        int answered = game.getTotalQuestions() - game.getRemainingQuestions();

        sessionManager.endQuiz(userId);

        return String.format(
                "🛑 *Игра остановлена*\n\n" +
                        "📊 Результаты текущей игры:\n" +
                        "✅ Правильных ответов: %d из %d\n\n" +
                        "Напишите /quiz, чтобы начать новую игру!",
                score, answered
        );
    }

    private String handleAnswer(long userId, String answer) {
        QuizGame game = sessionManager.getCurrentGame(userId);

        if (game == null) {
            return "❌ Нет активной игры!\nНапишите /quiz, чтобы начать викторину.";
        }

        boolean isCorrect = game.checkAnswer(answer);
        QuizQuestion currentQuestion = game.getCurrentQuestion();

        String result;
        if (isCorrect) {
            result = String.format(
                    "✅ *Правильно!*\n\n⭐ Счет: %d/%d",
                    game.getScore(), game.getTotalQuestions()
            );
        } else {
            result = String.format(
                    "❌ *Неправильно!*\n\nПравильный ответ: **%s**\n\n⭐ Счет: %d/%d",
                    currentQuestion.getCorrectAnswer(), game.getScore(), game.getTotalQuestions()
            );
        }

        if (game.isComplete()) {
            int finalScore = game.getScore();
            sessionManager.endQuiz(userId);
            return result + "\n\n🎉 *Поздравляем! Вы ответили на все вопросы!*\n\n" +
                    String.format("🏆 *Итоговый счет:* %d из %d\n\n/quiz - начать заново",
                            finalScore, game.getTotalQuestions());
        }

        QuizQuestion nextQuestion = game.getRandomQuestion();
        if (nextQuestion == null) {
            int finalScore = game.getScore();
            sessionManager.endQuiz(userId);
            return result + "\n\n🎉 *Викторина завершена!*\n\n" +
                    String.format("/quiz - начать новую игру", finalScore);
        }

        return result + "\n\n🔤 *Следующий вопрос:*\n" + nextQuestion.getQuestion() + "\n\nВведи ответ 👇";
    }
}