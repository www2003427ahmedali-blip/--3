package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class AiChatView extends LinearLayout {
    private EditText inputField;
    private Button sendButton;
    private LinearLayout messagesLayout;
    private ScrollView scrollView;
    private ArrayList<Message> messages = new ArrayList<>();
    private SharedPreferences prefs;

    public static class Message {
        String role; // "user" or "assistant"
        String content;
        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public AiChatView(Context context) {
        super(context);
        init(context);
    }

    public AiChatView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        prefs = DirectBootAwarePreferences.get_shared_preferences(context);
        inflate(context, R.layout.ai_chat_pane, this);
        inputField = findViewById(R.id.ai_chat_input);
        sendButton = findViewById(R.id.ai_chat_send);
        messagesLayout = findViewById(R.id.ai_chat_messages);
        scrollView = findViewById(R.id.ai_chat_scroll);

        sendButton.setOnClickListener(v -> sendMessage());
        inputField.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
    }

    private void sendMessage() {
        String text = inputField.getText().toString().trim();
        if (text.isEmpty()) return;

        String apiKey = prefs.getString("ai_chat_api_key", "");
        if (apiKey.isEmpty()) {
            addMessage(new Message("assistant", "Please set your OpenRouter API key in settings"));
            return;
        }

        addMessage(new Message("user", text));
        inputField.setText("");
        sendButton.setEnabled(false);

        new Thread(() -> {
            try {
                String response = callOpenRouter(text, apiKey);
                post(() -> {
                    addMessage(new Message("assistant", response));
                    sendButton.setEnabled(true);
                    scrollToBottom();
                });
            } catch (Exception e) {
                post(() -> {
                    addMessage(new Message("assistant", "Error: " + e.getMessage()));
                    sendButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void addMessage(Message msg) {
        messages.add(msg);
        TextView tv = new TextView(getContext());
        tv.setText(msg.role.equals("user") ? "You: " + msg.content : "AI: " + msg.content);
        tv.setPadding(16, 8, 16, 8);
        tv.setTextSize(14);
        messagesLayout.addView(tv);
        scrollToBottom();
    }

    private void scrollToBottom() {
        post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private String callOpenRouter(String prompt, String apiKey) throws Exception {
        URL url = new URL("https://openrouter.ai/api/v1/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("HTTP-Referer", "https://github.com/Julow/Unexpected-Keyboard");
        conn.setRequestProperty("X-Title", "Unexpected Keyboard AI Chat");
        conn.setDoOutput(true);

        JSONArray messagesArray = new JSONArray();
        for (Message m : messages) {
            JSONObject msgObj = new JSONObject();
            msgObj.put("role", m.role);
            msgObj.put("content", m.content);
            messagesArray.put(msgObj);
        }

        JSONObject body = new JSONObject();
        body.put("model", "google/gemma-2-9b-it:free");
        body.put("messages", messagesArray);
        body.put("max_tokens", 500);

        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes());
        os.flush();
        os.close();

        BufferedReader br = new BufferedReader(new InputStreamReader(
            conn.getResponseCode() == 200 ? conn.getInputStream() : conn.getErrorStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            response.append(line);
        }
        br.close();

        if (conn.getResponseCode() != 200) {
            throw new Exception("API error: " + response.toString());
        }

        JSONObject jsonResponse = new JSONObject(response.toString());
        return jsonResponse.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content");
    }
}
