/*
 * Copyright (C) 2010-2011 Fintan Fairmichael, Felix Bechstein
 * 
 * This file is part of WebSMS.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */
package com.fairmichael.fintan.websms.connector.fishtext;

import static com.fairmichael.fintan.websms.connector.fishtext.FishtextUtil.http;
import static com.fairmichael.fintan.websms.connector.fishtext.FishtextUtil.parametersMapToParametersArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;
import de.ub0r.android.websms.connector.common.Connector;
import de.ub0r.android.websms.connector.common.ConnectorCommand;
import de.ub0r.android.websms.connector.common.ConnectorSpec;
import de.ub0r.android.websms.connector.common.ConnectorSpec.SubConnectorSpec;
import de.ub0r.android.websms.connector.common.Log;
import de.ub0r.android.websms.connector.common.Utils;
import de.ub0r.android.websms.connector.common.WebSMSException;

/**
 * Connector for sending texts via fishtext.com.
 * 
 * @author flx
 * @author Fintan Fairmichael
 */
public class ConnectorFishtext extends Connector {
  /** Logging tag. */
  static final String TAG = "fishtext";
  /** Google's ad unit id. */
  private static final String AD_UNIT_ID = "a14e5aeb42ac986";
  /** Useragent for http communication. */
  public static final String USER_AGENT = "Mozilla/5.0 (Windows; U; "
      + "Windows NT 5.1; ko; rv:1.9.2.3) Gecko/20100401 Firefox/3.6.3 (.NET CLR 3.5.30729)";
  /** Preference name for using default number as login. */
  public static final String PREFS_LOGIN_WTIH_DEFAULT = "login_with_default";

  /** Login URL. */
  private static final String LOGIN_URL = "https://www.fishtext.com/cgi-bin/mobi/account";
  /** Login Referrer */
  private static final String LOGIN_REFERRER = "https://www.fishtext.com/cgi-bin/mobi/account";
  /** Get Balance URL */
  private static final String GET_BALANCE_URL = "https://www.fishtext.com/cgi-bin/mobi/getBalance.cgi";
  /** Send message page URL */
  private static final String SEND_MESSAGE_PAGE_URL = "https://www.fishtext.com/cgi-bin/mobi/sendMessage.cgi";
  /** Send SMS URL */
  private static final String SEND_SMS_URL = "https://www.fishtext.com/SendSMS/SendSMS";

  /** Used encoding. */
  static final String ENCODING = "ISO-8859-15";

  /** Pattern for extracting the balance from getBalance response */
  private static final Pattern LOGGED_IN_BALANCE = Pattern.compile("^(.*?)(\\d{1,}\\.\\d{1,})");
  /** Pattern for extracting the message id from the send message page */
  private static final Pattern MESSAGE_ID = Pattern.compile("<textarea class=\"messagelargeinput\" name=\"(\\w+)\" id=\"message\"");

  /** Preference identifier for notifying on successful send */
  private static final String SUCCESSFUL_SEND_NOTIFICATION_PREFERENCE_ID = "successful_send_notification_fishtext";

  private static final int MAXIMUM_MESSAGE_LENGTH = 459;

  @Override
  public final ConnectorSpec initSpec(final Context context) {
    Log.d(TAG, "Initing spec: " + PreferenceManager.getDefaultSharedPreferences(context).getAll());
    final String name = context.getString(R.string.connector_fishtext_name);
    ConnectorSpec c = new ConnectorSpec(name);
    c.setAuthor(context.getString(R.string.connector_fishtext_author));
    c.setAdUnitId(AD_UNIT_ID);
    c.setLimitLength(MAXIMUM_MESSAGE_LENGTH);
    // c.setSMSLengthCalculator(new BasicSMSLengthCalculator(new int[] { 160,
    // 146, 153 })); //TODO commented out until websms main app is updated
    c.setBalance(null);
    c.setCapabilities(ConnectorSpec.CAPABILITIES_UPDATE | ConnectorSpec.CAPABILITIES_SEND | ConnectorSpec.CAPABILITIES_PREFS);
    c.addSubConnector("fishtext", c.getName(), SubConnectorSpec.FEATURE_MULTIRECIPIENTS);
    return c;
  }

  @Override
  public final ConnectorSpec updateSpec(final Context context, final ConnectorSpec connectorSpec) {
    final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);
    if (p.getBoolean(Preferences.PREFS_ENABLED, false)) {
      if (p.getString(Preferences.PREFS_PASSWORD, "").length() > 0) {
        connectorSpec.setReady();
      } else {
        connectorSpec.setStatus(ConnectorSpec.STATUS_ENABLED);
      }
    } else {
      connectorSpec.setStatus(ConnectorSpec.STATUS_INACTIVE);
    }
    return connectorSpec;
  }

  /**
   * Test whether we're logged in
   * 
   * @param context
   * @return
   */
  public static boolean checkLoginAndGetBalance(final Context context, final ConnectorSpec spec) {
    // Load checkBalance, use regexp
    try {
      String response = FishtextUtil.http(context, GET_BALANCE_URL);

      final Matcher matcher = LOGGED_IN_BALANCE.matcher(response);
      if (matcher.find()) {
        String balance = matcher.group(0);
        balance = FishtextUtil.currencyFix(balance);
        Log.d(TAG, "Balance: " + balance);
        if (spec != null) {
          spec.setBalance(balance);
        }
        return true;
      } else {
        Log.d(TAG, "Get balance did not have a valid balance.");
        return false;
      }

    } catch (IOException ioe) {
      Log.d(TAG, "IOException when loading " + GET_BALANCE_URL);
      return false;
    }
  }

  public static void doLogin(final Context context, String login) {
    final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);

    Utils.clearCookies();
    Log.d(TAG, "Cleared cookies as we're about to login");

    if (login.startsWith("+")) {
      login = login.substring(1);
    } else if (login.startsWith("00")) {
      login = login.substring(2);
    }
    Log.d(TAG, "Login: " + login);

    ArrayList<BasicNameValuePair> postData = PostDataBuilder.start().add("mobile", login)
        .add("password", p.getString(Preferences.PREFS_PASSWORD, "")).add("rememberSession", "yes").add("_sp_errorJS", "0")
        .add("_sp_tooltip_init", "1").data();
    // Log.d(TAG, "Post data (WARNING PASSWORD VISIBLE!): " + postData);

    try {
      String loginResponse = FishtextUtil.http(context, LOGIN_URL, postData, LOGIN_REFERRER);

      if (!loginResponse.contains("Welcome back")) {
        Utils.clearCookies();
        Log.d(TAG, "Login did not succeed. Cleared cookies.");
        throw new WebSMSException(context, R.string.error_pw);
      }

    } catch (IOException ioe) {
      Log.d(TAG, "An IOException occurred during login. " + ioe);
      throw new WebSMSException(context, R.string.error_http);
    }
  }

  public static void ensureLoggedIn(final Context context, final ConnectorSpec spec, final String login, final boolean updateBalance) {
    Log.d(TAG, "Ensuring logged in.");
    if (!ConnectorFishtext.checkLoginAndGetBalance(context, spec)) {
      Log.d(TAG, "Not logged in, so doing login.");

      ConnectorFishtext.doLogin(context, login);
      // If we reach here without throwing an exception then we're logged in
      Log.d(TAG, "Should now be logged in");
      if (updateBalance) {
        // The aim was to update the balance, so do that now we're logged in
        ConnectorFishtext.checkLoginAndGetBalance(context, spec);
      }
    }
  }

  private void doSend(final Context context, final ConnectorCommand command) {
    // Prepare recipients
    final String[] recipients = command.getRecipients();
    final String[] recipientsProcessed = new String[recipients.length];
    // Map from processed recipient to original
    final Map<String, String> recipientMap = new HashMap<String, String>();

    for (int i = 0; i < recipients.length; i++) {
      final String number = Utils.getRecipientsNumber(recipients[i]);
      final String recipientProcessed = Utils.national2international(command.getDefPrefix(), number).substring(1);
      recipientMap.put(recipientProcessed, recipients[i]);
      recipientsProcessed[i] = recipientProcessed;
    }
    final String recipientsProcessedString = FishtextUtil.appendWithSeparator(recipientMap.keySet(), ",");
    Log.d(TAG, "Recipients string: " + recipientsProcessedString);
    Log.d(TAG, "Recipients map: " + recipientsProcessedString);

    try {
      final String sendMessagePage = FishtextUtil.http(context, SEND_MESSAGE_PAGE_URL);
      final Matcher matcher = MESSAGE_ID.matcher(sendMessagePage);
      if (!matcher.find()) {
        Log.d(TAG, "Could not find message id in send message page.");
        throw new WebSMSException(context, R.string.error_service);
      }
      final String messageId = matcher.group(1);
      Log.d(TAG, "MessageID: " + messageId);
      final String messageText = command.getText();
      final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
      String sendForFreePreference = sp.getString(SEND_FREE, SEND_FREE_FALSE);
      if (sendForFreePreference.equals(SEND_FREE_NOT_SET)) {
        sendForFreePreference = SEND_FREE_FALSE;
      }
      final ArrayList<BasicNameValuePair> postData = PostDataBuilder.start().add("action", "Send").add("SA", "0").add("DR", "1")
          .add("ST", sendForFreePreference).add(messageId, messageText).add("RN", recipientsProcessedString).data();
      Log.d(TAG, "Post data: " + postData);
      final String sentResponseText = FishtextUtil.http(context, SEND_SMS_URL, postData);
      this.examineSendResponse(context, sentResponseText, recipientMap);

    } catch (IOException ioe) {
      Log.d(TAG, "IOException occurred during send. " + ioe.toString());
      throw new WebSMSException(context, R.string.error_http);
    }
  }

  private static Pattern COST_PATTERN = Pattern.compile("at a cost of (.*?)(\\d{1,}\\.\\d{1,})");
  private static String COST_FREE = "free";
  private static String COST_UNKNOWN = "unknown";
  private static Pattern INVALID_NUMBERS_PATTERN = Pattern.compile("invalid number\\(s\\) (.*?) skipped");

  private void examineSendResponse(final Context context, final String response, final Map<String, String> recipientMap) {
    if (response.contains("Message sent")) {
      this.examineSuccessSendResponse(context, response, recipientMap);
    } else if (response.contains("Send failed")) {
      this.examineFailedSendResponse(context, response, recipientMap);
    } else {
      Log.d(TAG, "Send response didn't have Message Sent or Send Failed in it!");
      throw new WebSMSException(context, R.string.unexpected_error_fishtext);
    }
  }

  private static final Pattern SEND_FAILED_MESSAGE_PATTERN = Pattern.compile("<p>(.*)</p>");

  private void examineFailedSendResponse(final Context context, final String response, final Map<String, String> recipientMap) {
    Matcher matcher = SEND_FAILED_MESSAGE_PATTERN.matcher(response);
    if (matcher.find()) {
      throw new WebSMSException(context.getString(R.string.failed_send_fishtext, matcher.group(1)));
    } else {
      throw new WebSMSException(context.getString(R.string.failed_send_fishtext, ""));
    }
  }

  private void examineSuccessSendResponse(final Context context, final String response, final Map<String, String> recipientMap) {
    boolean sentToAll = response.contains("Your message was successfully sent to all recipients");
    String cost, costUnit;
    boolean free = response.contains("sent free") || response.contains(", free.");
    if (free) {
      cost = COST_FREE;
    }
    Matcher matcher = COST_PATTERN.matcher(response);
    if (matcher.find()) {
      cost = matcher.group(2);
      costUnit = FishtextUtil.currencyFix(matcher.group(1));
    } else {
      cost = COST_UNKNOWN;
      costUnit = "";
    }

    if (sentToAll) {
      // Sent to all successfully, just notify with the price
      final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);
      final boolean notifySend = p.getBoolean(SUCCESSFUL_SEND_NOTIFICATION_PREFERENCE_ID, true);
      if (notifySend) {
        final String notification = context.getString(R.string.successful_send_notification_fishtext_notification, costUnit + cost);
        Log.d(TAG, "Notifying on successful send: " + notification);
        FishtextUtil.toastNotifyOnMain(context, notification, Toast.LENGTH_SHORT);
      } else {
        Log.d(TAG, "Not notifying on successful send");
      }
    } else {
      // Process invalids
      String invalids = "";
      int invalidCount = 0;
      Matcher invalidMatcher = INVALID_NUMBERS_PATTERN.matcher(response);
      if (invalidMatcher.find()) {
        Log.d(TAG, "Matched invalids " + invalidMatcher.group() + " - " + invalidMatcher.group(1));
        invalids = invalidMatcher.group(1);

        String[] parts = invalids.split(",");
        for (int i = 0; i < parts.length; i++) {
          parts[i] = recipientMap.get(parts[i].trim());
        }

        invalids = FishtextUtil.appendWithSeparator(parts, ", ");
        invalidCount = parts.length;
      }
      int successfulCount = recipientMap.size() - invalidCount;

      if (successfulCount > 0) {
        // Sent to some
        String errorMessage = context.getString(R.string.unsuccessful_send_some_fishtext, successfulCount, costUnit + cost, invalids);
        throw new WebSMSException(errorMessage);
      } else {
        // Sent to none
        String errorMessage = context.getString(R.string.unsuccessful_send_all_fishtext, recipientMap.size(), invalids);
        throw new WebSMSException(errorMessage);
      }

    }
  }

  public static final String SEND_FREE = "send_free_fishtext";
  public static final String SEND_FREE_LAST_SET = "send_free_fishtext_last_set";
  public static final String SEND_FREE_NOT_SET = "-1";
  public static final String SEND_FREE_TRUE = "1";
  public static final String SEND_FREE_FALSE = "0";

  private static final String SETTINGS_URL = "https://www.fishtext.com/cgi-bin/ajax/settings.cgi";

  private static final Pattern INPUT_PATTERN = Pattern.compile("\\<input .*?value=\"(.*?)\".*?name=\"(.*?)\"");
  private static final Pattern SEND_TYPE_SELECT_PATTERN = Pattern.compile("<select class=\"selectSettings\" id=\"sendType\"(.*?)<\\/select>",
      Pattern.DOTALL);
  private static final Pattern SELECTED_OPTION_PATTERN = Pattern.compile("<option value=\"([^\"]*?)\" selected");
  private static final Pattern OPTION_PATTERN = Pattern.compile("<option value=\"([^\"]*?)\"");
  private static final Pattern SEND_FROM_SELECT_PATTERN = Pattern.compile("<select class=\"selectSettings\" id=\"sendFrom\"(.*?)<\\/select>",
      Pattern.DOTALL);

  private static final String[] REQUIRED_SETTINGS = new String[] { "sendFrom", "sendType", "firstName", "lastName", "emailAddress" };

  // TODO should generalise to taking a Map<String,String> of new settings so we
  // can reuse for other settings (sender, for example)
  private static void updateSettingsIfNecessary(final Context context) {
    // Check send preference and what was last set.
    final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);

    String sendForFreePreference = p.getString(SEND_FREE, SEND_FREE_NOT_SET);
    Log.d(TAG, "sendForFreePreference=" + sendForFreePreference);

    if (sendForFreePreference.equals(SEND_FREE_NOT_SET)) {
      Log.d(TAG, "Not set, so just setting last set to that.");
      p.edit().putString(SEND_FREE_LAST_SET, SEND_FREE_NOT_SET).commit();
      return;
    } else {
      String lastSet = p.getString(SEND_FREE_LAST_SET, SEND_FREE_NOT_SET);
      Log.d(TAG, "lastSet=" + lastSet);
      if (!sendForFreePreference.equals(lastSet)) {
        // We need to update. Update if possible. On success, update lastSet. On
        // failure update to NOT_SET so it will be tried next time

        boolean success = false;

        // Load settings page

        try {
          // Post to settings url, no/empty postdata
          final String settingsPage = FishtextUtil.http(context, SETTINGS_URL, new ArrayList<BasicNameValuePair>(0));
          Log.d(TAG, "Loaded settings page, getting values we need");

          // Extract the inputs
          final Matcher inputMatcher = INPUT_PATTERN.matcher(settingsPage);
          final Map<String, String> formMap = new HashMap<String, String>();
          while (inputMatcher.find()) {
            formMap.put(inputMatcher.group(2), inputMatcher.group(1));
          }

          // Get selected sendType
          final Matcher sendTypeMatcher = SEND_TYPE_SELECT_PATTERN.matcher(settingsPage);
          if (sendTypeMatcher.find()) {
            String selectText = sendTypeMatcher.group();
            final Matcher actualSendTypeMatcher = SELECTED_OPTION_PATTERN.matcher(selectText);
            if (actualSendTypeMatcher.find()) {
              Log.d(TAG, "Found the sendType as " + actualSendTypeMatcher.group(1));
              formMap.put("sendType", actualSendTypeMatcher.group(1));
            } else {
              Log.d(TAG, "Nothing selected, using the first option");
              Matcher optionMatcher = OPTION_PATTERN.matcher(selectText);
              if (optionMatcher.find()) {
                formMap.put("sendType", optionMatcher.group());
              }
            }
          }

          // Get selected sendFrom
          final Matcher sendFromMatcher = SEND_FROM_SELECT_PATTERN.matcher(settingsPage);
          if (sendFromMatcher.find()) {
            String selectText = sendFromMatcher.group();
            final Matcher actualSendFromMatcher = SELECTED_OPTION_PATTERN.matcher(selectText);
            if (actualSendFromMatcher.find()) {
              Log.d(TAG, "Found the sendFrom as " + actualSendFromMatcher.group(1));
              formMap.put("sendFrom", actualSendFromMatcher.group(1));
            } else {
              Log.d(TAG, "Nothing selected, using the first option");
              Matcher optionMatcher = OPTION_PATTERN.matcher(selectText);
              if (optionMatcher.find()) {
                formMap.put("sendFrom", optionMatcher.group());
              }
            }
          }

          Log.d(TAG, "Got the following from the settings page: " + formMap);

          String existingSendType = formMap.get("sendType");
          if (sendForFreePreference.equals(existingSendType)) {
            Log.d(TAG, "Send type is already set to what we want it to be (" + sendForFreePreference + ")");
            success = true;
          } else {
            final Map<String, String> updatedFormMap = new HashMap<String, String>();
            for (String requiredSetting : REQUIRED_SETTINGS) {
              updatedFormMap.put(requiredSetting, formMap.get(requiredSetting));
            }

            Log.d(TAG, "Extracted what we needed: " + updatedFormMap);

            updatedFormMap.put("sendType", sendForFreePreference);
            updatedFormMap.put("action", "saveSettings");
            updatedFormMap.put("_sp_errorJS", "0");
            updatedFormMap.put("_sp_tooltip_init", "0");

            Log.d(TAG, "Added a few params: " + updatedFormMap);

            final String sentSettingsPage = http(context, SETTINGS_URL, parametersMapToParametersArray(updatedFormMap));
            if (sentSettingsPage.contains("Your details have been updated")) {
              Log.d(TAG, "Successfully updated the settings. Yay!");
              success = true;
            } else {
              Log.d(TAG, "Received settings update response, but no confirmation contained within");
            }
          }

        } catch (IOException ioe) {
          Log.d(TAG, "IOException during get/set settings. Send preference not set");
        }

        if (success) {
          Log.d(TAG, "Success on setting preference, setting last set to " + sendForFreePreference);
          p.edit().putString(SEND_FREE_LAST_SET, sendForFreePreference).commit();
        } else {
          Log.d(TAG, "Failed to set preference, so setting last set to not set (" + SEND_FREE_NOT_SET + ")");
          p.edit().putString(SEND_FREE_LAST_SET, SEND_FREE_NOT_SET).commit();
        }

      } else {
        Log.d(TAG, "Last set is the same as what we're trying to set. No action required.");
      }
    }

  }

  private static String getLogin(final Context context, final ConnectorCommand command) {
    final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);
    final String login = p.getBoolean(PREFS_LOGIN_WTIH_DEFAULT, false) ? command.getDefSender() : Utils.getSender(context, command.getDefSender());
    return login;
  }

  @Override
  protected final void doUpdate(final Context context, final Intent intent) {
    final ConnectorSpec spec = this.getSpec(context);
    final ConnectorCommand command = new ConnectorCommand(intent);
    ConnectorFishtext.ensureLoggedIn(context, spec, getLogin(context, command), true);
  }

  @Override
  protected final void doSend(final Context context, final Intent intent) {
    final ConnectorSpec spec = this.getSpec(context);
    final ConnectorCommand command = new ConnectorCommand(intent);
    // Ensure logged in
    ConnectorFishtext.ensureLoggedIn(context, spec, getLogin(context, command), false);

    // Set send preferences on fishtext if anything has changed or we have not
    // set before.
    ConnectorFishtext.updateSettingsIfNecessary(context);

    // Do actual send
    this.doSend(context, command);
    // Update balance
    ConnectorFishtext.checkLoginAndGetBalance(context, spec);
  }
}
