package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SubtitleTranslationPrefs;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

import java.util.ArrayList;
import java.util.List;

public class SubtitleSettingsPresenter extends BasePresenter<Void> {
    private final PlayerData mPlayerData;

    public SubtitleSettingsPresenter(Context context) {
        super(context);
        mPlayerData = PlayerData.instance(context);
    }

    public static SubtitleSettingsPresenter instance(Context context) {
        return new SubtitleSettingsPresenter(context);
    }

    public void show() {
        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        settingsPresenter.appendSingleSwitch(AppDialogUtil.createSubtitleChannelOption(getContext()));
        // Can't work properly. There is no robust language detection.
        //appendSubtitleLanguageCategory(settingsPresenter);
        //appendMoreSubtitlesSwitch(settingsPresenter);
        appendSubtitleStyleCategory(settingsPresenter);
        appendSubtitleSizeCategory(settingsPresenter);
        appendSubtitlePositionCategory(settingsPresenter);
        appendContextualTranslationOptions(settingsPresenter);

        settingsPresenter.showDialog(getContext().getString(R.string.subtitle_category_title));
    }

    //private void appendSubtitleLanguageCategory(AppSettingsPresenter settingsPresenter) {
    //    String subtitleLanguageTitle = getContext().getString(R.string.subtitle_language);
    //    String subtitlesDisabled = getContext().getString(R.string.subtitles_disabled);
    //
    //    LangUpdater langUpdater = new LangUpdater(getContext());
    //    Map<String, String> locales = langUpdater.getSupportedLocales();
    //    FormatItem currentFormat = mPlayerData.getFormat(FormatItem.TYPE_SUBTITLE);
    //
    //    List<OptionItem> options = new ArrayList<>();
    //
    //    options.add(UiOptionItem.from(
    //            subtitlesDisabled, option -> mPlayerData.setFormat(FormatItem.fromLanguage(null)),
    //            currentFormat == null || currentFormat.equals(FormatItem.fromLanguage(null))));
    //
    //    for (Entry<String, String> entry : locales.entrySet()) {
    //        if (entry.getValue().isEmpty()) {
    //            // Remove default language entry
    //            continue;
    //        }
    //
    //        options.add(UiOptionItem.from(
    //                entry.getKey(), option -> mPlayerData.setFormat(FormatItem.fromLanguage(entry.getValue())),
    //                FormatItem.fromLanguage(entry.getValue()).equals(currentFormat)));
    //    }
    //
    //    settingsPresenter.appendRadioCategory(subtitleLanguageTitle, options);
    //}

    private void appendSubtitleStyleCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createSubtitleStylesCategory(getContext());
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendSubtitleSizeCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createSubtitleSizeCategory(getContext());
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendSubtitlePositionCategory(AppDialogPresenter settingsPresenter) {
        OptionCategory category = AppDialogUtil.createSubtitlePositionCategory(getContext());
        settingsPresenter.appendRadioCategory(category.title, category.options);
    }

    private void appendMoreSubtitlesSwitch(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from("Unlock more subtitles",
                option -> MediaServiceData.instance().setMoreSubtitlesUnlocked(option.isSelected()),
                MediaServiceData.instance().isMoreSubtitlesUnlocked()));
    }

    private void appendContextualTranslationOptions(AppDialogPresenter settingsPresenter) {
        SubtitleTranslationPrefs prefs = SubtitleTranslationPrefs.instance(getContext());

        settingsPresenter.appendSingleSwitch(UiOptionItem.from(
                getContext().getString(R.string.subtitle_contextual_enable),
                option -> prefs.setEnabled(option.isSelected()),
                prefs.isEnabled()
        ));

        List<OptionItem> providers = new ArrayList<>();
        providers.add(UiOptionItem.from(
                getContext().getString(R.string.subtitle_contextual_provider_gemini),
                option -> prefs.setProvider(SubtitleTranslationPrefs.PROVIDER_GEMINI),
                SubtitleTranslationPrefs.PROVIDER_GEMINI.equals(prefs.getProvider())
        ));
        providers.add(UiOptionItem.from(
                getContext().getString(R.string.subtitle_contextual_provider_openai),
                option -> prefs.setProvider(SubtitleTranslationPrefs.PROVIDER_OPENAI),
                SubtitleTranslationPrefs.PROVIDER_OPENAI.equals(prefs.getProvider())
        ));
        settingsPresenter.appendRadioCategory(
                getContext().getString(R.string.subtitle_contextual_provider),
                providers
        );

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.subtitle_contextual_api_key),
                option -> SimpleEditDialog.showPassword(
                        getContext(),
                        getContext().getString(R.string.subtitle_contextual_api_key),
                        prefs.getApiKey(),
                        newValue -> {
                            prefs.setApiKey(newValue);
                            return true;
                        }
                )
        ));

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.subtitle_contextual_model),
                option -> SimpleEditDialog.show(
                        getContext(),
                        getContext().getString(R.string.subtitle_contextual_model),
                        prefs.getEffectiveModel(),
                        prefs.getRawModel(),
                        newValue -> {
                            prefs.setModel(newValue);
                            return true;
                        }
                )
        ));

        settingsPresenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.subtitle_contextual_endpoint),
                option -> SimpleEditDialog.show(
                        getContext(),
                        getContext().getString(R.string.subtitle_contextual_endpoint),
                        SubtitleTranslationPrefs.DEFAULT_OPENAI_ENDPOINT,
                        prefs.getRawEndpoint(),
                        newValue -> {
                            prefs.setEndpoint(newValue);
                            return true;
                        }
                )
        ));
    }
}
