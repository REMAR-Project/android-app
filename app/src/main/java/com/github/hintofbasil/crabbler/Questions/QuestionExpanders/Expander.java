package com.github.hintofbasil.crabbler.Questions.QuestionExpanders;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.CountDownTimer;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.github.hintofbasil.crabbler.Keys;
import com.github.hintofbasil.crabbler.Questions.QuestionActivity;
import com.github.hintofbasil.crabbler.Questions.QuestionReader;
import com.github.hintofbasil.crabbler.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interface to expand a layout using a question JSON object
 */
public abstract class Expander {

    AppCompatActivity activity;
    SharedPreferences prefs;
    int questionId;
    JSONObject questionJson;

    public Expander(AppCompatActivity activity, JSONObject questionJson) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences(Keys.SAVED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        this.questionId = activity.getIntent().getIntExtra(Keys.QUESTION_ID_KEY, 0);
        this.questionJson = questionJson;
    }

    public abstract void expandLayout() throws JSONException;

    protected abstract void setPreviousAnswer(String answer);

    public abstract String getSelectedAnswer();

    public void nextQuestion(int delay) {
        //TODO handle last question
        final Intent intent = new Intent(activity, QuestionActivity.class);
        intent.putExtra(Keys.QUESTION_ID_KEY, questionId + 1);
        new CountDownTimer(delay, delay) {

            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                try {
                    saveAnswer();
                    activity.startActivity(intent);
                    activity.finish();
                } catch (IOException|JSONException e) {
                    Log.e("Expander", "Unable to save answer\n" + Log.getStackTraceString(e));
                }

            }
        }.start();
    }

    private String[] getCurrentAnswers() throws IOException, JSONException {
        String answers = prefs.getString(Keys.ANSWERS_KEY, null);
        if(answers==null) {
            Log.i("Expander", "No current answers");
            //Create empty answers string
            QuestionReader qr = new QuestionReader();
            int noAnswers = qr.getJsonQuestions(activity).length();
            char[] buffer = new char[noAnswers-1];
            Arrays.fill(buffer, ',');
            answers = new String(buffer);
        }
        return answers.split(",", -1);
    }

    protected String getCurrentAnswer() throws IOException, JSONException {
        return getAnswer(questionId);
    }

    protected String getAnswer(int id) throws IOException, JSONException {
        // Can go out of bounds on questions.json update
        try {
            return getCurrentAnswers()[id];
        } catch (ArrayIndexOutOfBoundsException e) {
            // Recursively add commas until correct length
            // TODO check for question change on boot instead
            String answers = prefs.getString(Keys.ANSWERS_KEY, null);
            prefs.edit().putString(Keys.ANSWERS_KEY, answers + ",").commit();
            return getCurrentAnswer();
        }
    }

    private void saveAnswer() throws IOException, JSONException {
        String[] answers = getCurrentAnswers();
        String answer = getSelectedAnswer();
        // Don't save on null response
        if(answer==null) {
            return;
        }
        answers[questionId] = answer;
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<answers.length;i++) {
            if(i==questionId) {
                sb.append(answer);
            } else {
                sb.append(answers[i]);
            }
            sb.append(',');
        }
        int len = sb.length();
        String newAnswers = sb.substring(0, len - 1);
        prefs.edit().putString(Keys.ANSWERS_KEY, newAnswers).apply();
        Log.i("Expander", "Question saved - " + newAnswers);
    }

    protected Drawable getDrawable(String name) {
        if(name.contains(".")) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        int drawableId = activity.getResources().getIdentifier(name, "drawable", activity.getPackageName());
        return ContextCompat.getDrawable(activity, drawableId);
    }

    public void setPreviousAnswer() {
        try {
            setPreviousAnswer(getCurrentAnswer());
        } catch (IOException|JSONException e) {
            Log.e("Expander", "Unable to load answer\n" + Log.getStackTraceString(e));
        }
    }

    public void previousQuestion() {
        final Intent intent = new Intent(activity, QuestionActivity.class);
        intent.putExtra(Keys.QUESTION_ID_KEY, questionId - 1);
        activity.startActivity(intent);
        activity.finish();
    }

    public String getQuestionString(String string) throws JSONException {
        String questionAttr = questionJson.getString(string);
        // Return early if no expanding
        if(!questionAttr.contains("(")) {
            return questionAttr;
        }
        StringBuilder sb = new StringBuilder(questionAttr);
        Pattern pattern = Pattern.compile("\\(0\\)");
        Matcher match = pattern.matcher(questionAttr);
        while(match.find()) {
            String found = match.group();
            // Remove brackets
            String number = found.substring(1, found.length()-1);
            try {
                String answer = getAnswer(Integer.parseInt(number));
                sb.replace(match.start(), match.end(), answer);
            } catch (IOException e) {
                Log.e("Expander", "Invalid question id in " + questionAttr);
                return "";
            }
        }
        return sb.toString();
    }
}
