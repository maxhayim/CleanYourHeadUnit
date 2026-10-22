package com.gathin.photodream;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

/**
 * The module card on cleanyourtv's Home: the same scene as the screensaver, right away, until Back. Menu opens
 * the settings. The screen stays on while it shows.
 */
public class SlideshowActivity extends Activity {

    private Scene scene;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Built again on every start, so options changed in the settings show at once.
        scene = new Scene(this);
        setContentView(scene.root);
        scene.start();
    }

    @Override
    protected void onStop() {
        if (scene != null) scene.stop();
        scene = null;
        super.onStop();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
