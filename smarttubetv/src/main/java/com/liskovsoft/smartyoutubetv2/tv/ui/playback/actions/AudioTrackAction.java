package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for directly opening the audio track / dubbing selection dialog.
 */
public class AudioTrackAction extends PaddingAction {
    public AudioTrackAction(Context context) {
        super(R.id.action_audio_track);
        Drawable uncoloredDrawable = ContextCompat.getDrawable(context, R.drawable.action_audio_track);

        setIcon(uncoloredDrawable);
        setLabel1(context.getString(R.string.action_audio_track));
    }
}
