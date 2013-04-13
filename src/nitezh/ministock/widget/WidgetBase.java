/*
 The MIT License

 Copyright (c) 2013 Nitesh Patel http://niteshpatel.github.io/ministocks

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 */

package nitezh.ministock.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.RemoteViews;
import nitezh.ministock.*;
import nitezh.ministock.DataSource.StockField;
import nitezh.ministock.UserData.PortfolioField;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

public class WidgetBase extends AppWidgetProvider {

    // Update type
    public static final int VIEW_UPDATE = 0;
    public static final int VIEW_CHANGE = 1;
    public static final int VIEW_NO_UPDATE = 2;
    // View type
    public static final int VIEW_DAILY_PERCENT = 0;
    public static final int VIEW_DAILY_CHANGE = 1;
    public static final int VIEW_PORTFOLIO_PERCENT = 2;
    public static final int VIEW_PORTFOLIO_CHANGE = 3;
    public static final int VIEW_PORTFOLIO_PERCENT_AER = 4;
    public static final int VIEW_PL_DAILY_PERCENT = 5;
    public static final int VIEW_PL_DAILY_CHANGE = 6;
    public static final int VIEW_PL_PERCENT = 7;
    public static final int VIEW_PL_CHANGE = 8;
    public static final int VIEW_PL_PERCENT_AER = 9;
    // Static variables used by the alarm manager
    public static final String ALARM_UPDATE = "nitezh.ministock.ALARM_UPDATE";
    // Colours
    public static int COLOUR_GAIN = Color.parseColor("#CCFF66");
    public static int COLOUR_LOSS = Color.parseColor("#FF6666");
    public static int COLOUR_ALERT_HIGH = Color.parseColor("#FFEE33");
    public static int COLOUR_ALERT_LOW = Color.parseColor("#FF66FF");
    public static int COLOUR_VOLUME = Color.LTGRAY;
    public static int COLOUR_NA = Color.parseColor("#66CCCC");

    public static RemoteViews getRemoteViews(
            Context context,
            SharedPreferences prefs,
            int widgetSize) {

        // Retrieve background preference and layoutId
        String background = prefs.getString("background", "transparent");

        Integer drawableId;
        if (background.equals("transparent")) {
            if (prefs.getBoolean("large_font", false)) {
                drawableId = R.drawable.ministock_bg_transparent68_large;
            } else {
                drawableId = R.drawable.ministock_bg_transparent68;
            }

        } else if (background.equals("none")) {
            drawableId = R.drawable.blank;

        } else {
            if (prefs.getBoolean("large_font", false)) {
                drawableId = R.drawable.ministock_bg_large;
            } else {
                drawableId = R.drawable.ministock_bg;
            }
        }

        // Return the matching remote views instance
        RemoteViews views;
        if (widgetSize == 1) {
            if (prefs.getBoolean("large_font", false)) {
                views =
                        new RemoteViews(
                                context.getPackageName(),
                                R.layout.widget_1x4_large);
            } else {
                views =
                        new RemoteViews(
                                context.getPackageName(),
                                R.layout.widget_1x4);
            }
        } else if (widgetSize == 2) {
            if (prefs.getBoolean("large_font", false)) {
                views =
                        new RemoteViews(
                                context.getPackageName(),
                                R.layout.widget_2x2_large);
            } else {
                views =
                        new RemoteViews(
                                context.getPackageName(),
                                R.layout.widget_2x2);
            }
        } else if (widgetSize == 3) {
            if (prefs.getBoolean("large_font", false)) {
                views =
                        new RemoteViews(
                                context.getPackageName(),
                                R.layout.widget_2x4_large);
            } else {
                views =
                        new RemoteViews(
                                context.getPackageName(),
                                R.layout.widget_2x4);
            }
        } else {
            if (prefs.getBoolean("large_font", false)) {
                views =
                        new RemoteViews(
                                context.getPackageName(),
                                R.layout.widget_1x2_large);
            } else {
                views =
                        new RemoteViews(
                                context.getPackageName(),
                                R.layout.widget_1x2);
            }
        }

        views.setImageViewResource(R.id.widget_bg, drawableId);
        return views;
    }

    public static int getStockViewId(int line, int col) {
        return Utils.getField("text" + line + col);
    }

    // Display the correct number of widget rows
    public static void displayRows(RemoteViews views, int arraySize) {

        for (int i = 1; i < 11; i++)
            views.setViewVisibility(Utils.getField("line" + i), View.GONE);

        for (int i = 1; i < arraySize + 1; i++)
            views.setViewVisibility(Utils.getField("line" + i), View.VISIBLE);
    }

    // Global formatter so we can perform global text formatting in one place
    public static SpannableString bolden(SharedPreferences prefs, String s) {

        SpannableString span = new SpannableString(s);

        if (prefs.getString("text_style", "normal").equals("bold"))
            span.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), 0);

        else
            span.setSpan(new StyleSpan(Typeface.NORMAL), 0, s.length(), 0);

        return span;
    }

    public static HashMap<String, Object> getFormattedRow(
            String symbol,
            HashMap<StockField, String> quoteInfo,
            HashMap<String, HashMap<PortfolioField, String>> portfolioStockMap,
            int widgetView,
            SharedPreferences prefs,
            int widgetSize) {

        // Create the HashMap for our return values
        HashMap<String, Object> rowItems = new HashMap<String, Object>();

        // Initialise columns
        rowItems.put("COL0_VALUE", symbol);
        rowItems.put("COL0_COLOUR", Color.WHITE);
        rowItems.put("COL1_VALUE", "");
        rowItems.put("COL1_COLOUR", Color.WHITE);
        rowItems.put("COL2_VALUE", "");
        rowItems.put("COL2_COLOUR", Color.WHITE);
        rowItems.put("COL3_VALUE", "");
        rowItems.put("COL3_COLOUR", Color.WHITE);
        rowItems.put("COL4_VALUE", "");
        rowItems.put("COL4_COLOUR", Color.WHITE);

        // Set the stock symbol, and strip off exchange suffix
        // if requested in the preferences

        if (prefs.getBoolean("hide_suffix", false)) {
            int dotIndex = symbol.indexOf(".");
            if (dotIndex > -1) {
                rowItems.put("COL0_VALUE", symbol.substring(0, dotIndex));
            }
        }

        // If there is no quote info return immediately
        if (quoteInfo == null
                || quoteInfo.get(StockField.PRICE) == null
                || quoteInfo.get(StockField.PERCENT) == null) {

            if (widgetSize == 0 || widgetSize == 2) {
                rowItems.put("COL1_VALUE", "no");
                rowItems.put("COL1_COLOUR", Color.GRAY);
                rowItems.put("COL2_VALUE", "data");
                rowItems.put("COL2_COLOUR", Color.GRAY);

            } else {
                rowItems.put("COL3_VALUE", "no");
                rowItems.put("COL3_COLOUR", Color.GRAY);
                rowItems.put("COL4_VALUE", "data");
                rowItems.put("COL4_COLOUR", Color.GRAY);
            }
            return rowItems;
        }

        // Retrieve quote info
        String name = quoteInfo.get(StockField.NAME);
        String price = quoteInfo.get(StockField.PRICE);
        String change = quoteInfo.get(StockField.CHANGE);
        String percent = quoteInfo.get(StockField.PERCENT);
        String volume = quoteInfo.get(StockField.VOLUME);

        // Get the buy info for this stock from the portfolio
        HashMap<PortfolioField, String> stockInfo =
                portfolioStockMap.get(symbol);

        // Set default values
        if (widgetSize == 1 || widgetSize == 3) {
            rowItems.put("COL0_VALUE", name);
            rowItems.put("COL2_VALUE", formatVolume(volume));
            rowItems.put("COL2_COLOUR", COLOUR_VOLUME);
            rowItems.put("COL3_VALUE", change);
            rowItems.put("COL3_COLOUR", COLOUR_NA);
            rowItems.put("COL4_VALUE", percent);
            rowItems.put("COL4_COLOUR", COLOUR_NA);

        } else {
            rowItems.put("COL2_VALUE", percent);
            rowItems.put("COL2_COLOUR", COLOUR_NA);
        }

        // Override the name if a custom value has been provided
        if (stockInfo != null
                && stockInfo.containsKey(PortfolioField.CUSTOM_DISPLAY)
                && !stockInfo.get(PortfolioField.CUSTOM_DISPLAY).equals("")) {
            rowItems.put(
                    "COL0_VALUE",
                    stockInfo.get(PortfolioField.CUSTOM_DISPLAY));
        }

        // Set the price
        rowItems.put("COL1_VALUE", price);

        // Initialise variables for values
        String daily_change = quoteInfo.get(StockField.CHANGE);
        String daily_percent = quoteInfo.get(StockField.PERCENT);

        String total_change = null;
        String total_percent = null;
        String aer_change = null;
        String aer_percent = null;

        String pl_holding = null;
        String pl_daily_change = null;
        String pl_total_change = null;
        String pl_aer_change = null;
        Double years_elapsed = null;
        Boolean limithigh_triggered = false;
        Boolean limitlow_triggered = false;

        Double d_price = Tools.parseDouble(price, null);
        Double d_dailychange = Tools.parseDouble(change, null);

        Double d_buyprice = null;
        Double d_quantity = null;
        Double d_limithigh = null;
        Double d_limitlow = null;

        try {
            d_buyprice = Tools.parseDouble(stockInfo.get(PortfolioField.PRICE));
        } catch (Exception e) {
        }

        try {
            d_quantity =
                    Tools.parseDouble(stockInfo.get(PortfolioField.QUANTITY));
        } catch (Exception e) {
        }

        try {
            d_limithigh =
                    Tools.parseDouble(stockInfo.get(PortfolioField.LIMIT_HIGH));
        } catch (Exception e) {
        }

        try {
            d_limitlow =
                    Tools.parseDouble(stockInfo.get(PortfolioField.LIMIT_LOW));
        } catch (Exception e) {
        }

        Double d_pricechange = null;
        try {
            d_pricechange = d_price - d_buyprice;
        } catch (Exception e) {
        }

        try {
            Date date =
                    new SimpleDateFormat("yyyy-MM-dd").parse(stockInfo
                            .get(PortfolioField.DATE));
            double elapsed = (new Date().getTime() - date.getTime()) / 1000;
            years_elapsed = elapsed / 31536000;
        } catch (Exception e) {
        }

        // total_change
        if (d_pricechange != null)
            total_change = Tools.getTrimmedDouble(d_pricechange, 5);

        // total_percent
        if (d_pricechange != null)
            total_percent =
                    String.format("%.1f", 100 * (d_pricechange / d_buyprice))
                            + "%";

        // aer_change
        if (d_pricechange != null && years_elapsed != null)
            aer_change =
                    Tools.getTrimmedDouble(d_pricechange / years_elapsed, 5);

        // aer_percent
        if (d_pricechange != null && years_elapsed != null)
            aer_percent =
                    String.format("%.1f", (100 * (d_pricechange / d_buyprice))
                            / years_elapsed)
                            + "%";

        // pl_holding
        if (d_price != null && d_quantity != null)
            pl_holding = String.format("%.0f", d_price * d_quantity);

        // pl_daily_change
        if (d_dailychange != null && d_quantity != null)
            pl_daily_change = String.format("%.0f", d_dailychange * d_quantity);

        // pl_total_change
        if (d_pricechange != null && d_quantity != null)
            pl_total_change = String.format("%.0f", d_pricechange * d_quantity);

        // pl_aer_change
        if (d_pricechange != null
                && d_quantity != null
                && years_elapsed != null)
            pl_aer_change =
                    String.format("%.0f", (d_pricechange * d_quantity)
                            / years_elapsed);

        // limithigh_trigerred
        if (d_price != null && d_limithigh != null)
            limithigh_triggered = d_price > d_limithigh;

        // limitlow_triggered
        if (d_price != null && d_limitlow != null)
            limitlow_triggered = d_price < d_limitlow;

        Boolean pl_view = false;
        Boolean pl_change = false;

        String column1 = null;
        String column2 = null;
        String column3 = null;
        String column4 = null;

        if (widgetSize == 0 || widgetSize == 2) {

            switch (widgetView) {
                case VIEW_DAILY_PERCENT:
                    column2 = daily_percent;
                    break;

                case VIEW_DAILY_CHANGE:
                    column2 = daily_change;
                    break;

                case VIEW_PORTFOLIO_PERCENT:
                    column2 = total_percent;
                    break;

                case VIEW_PORTFOLIO_CHANGE:
                    column2 = total_change;
                    break;

                case VIEW_PORTFOLIO_PERCENT_AER:
                    column2 = aer_percent;
                    break;

                case VIEW_PL_DAILY_PERCENT:
                    pl_view = true;
                    column1 = pl_holding;
                    column2 = daily_percent;
                    break;

                case VIEW_PL_DAILY_CHANGE:
                    pl_view = true;
                    pl_change = true;
                    column1 = pl_holding;
                    column2 = pl_daily_change;
                    break;

                case VIEW_PL_PERCENT:
                    pl_view = true;
                    column1 = pl_holding;
                    column2 = total_percent;
                    break;

                case VIEW_PL_CHANGE:
                    pl_view = true;

                    pl_change = true;
                    column1 = pl_holding;
                    column2 = pl_total_change;
                    break;

                case VIEW_PL_PERCENT_AER:
                    pl_view = true;
                    column1 = pl_holding;
                    column2 = aer_percent;
                    break;
            }

        } else {
            switch (widgetView) {
                case VIEW_DAILY_PERCENT:
                    column3 = daily_change;
                    column4 = daily_percent;
                    break;

                case VIEW_DAILY_CHANGE:
                    column3 = daily_change;
                    column4 = daily_percent;
                    break;

                case VIEW_PORTFOLIO_PERCENT:
                    column3 = total_change;
                    column4 = total_percent;
                    break;

                case VIEW_PORTFOLIO_CHANGE:
                    column3 = total_change;
                    column4 = total_percent;
                    break;

                case VIEW_PORTFOLIO_PERCENT_AER:
                    column3 = aer_change;
                    column4 = aer_percent;
                    break;

                case VIEW_PL_DAILY_PERCENT:
                    pl_view = true;
                    pl_change = true;
                    column1 = pl_holding;
                    column3 = pl_daily_change;
                    column4 = daily_percent;
                    break;

                case VIEW_PL_DAILY_CHANGE:
                    pl_view = true;
                    pl_change = true;
                    column1 = pl_holding;
                    column3 = pl_daily_change;
                    column4 = daily_percent;
                    break;

                case VIEW_PL_PERCENT:
                    pl_view = true;
                    pl_change = true;
                    column1 = pl_holding;
                    column3 = pl_total_change;
                    column4 = total_percent;
                    break;

                case VIEW_PL_CHANGE:
                    pl_view = true;
                    pl_change = true;
                    column1 = pl_holding;
                    column3 = pl_total_change;
                    column4 = total_percent;
                    break;

                case VIEW_PL_PERCENT_AER:
                    pl_view = true;
                    pl_change = true;
                    column1 = pl_holding;
                    column3 = pl_aer_change;
                    column4 = aer_percent;
                    break;
            }
        }

        // Set the price column colour if we have hit an alert
        // (this is only relevant for non-profit and loss views)
        if (limithigh_triggered && !pl_view) {
            rowItems.put("COL1_COLOUR", COLOUR_ALERT_HIGH);
        }
        if (limitlow_triggered && !pl_view) {
            rowItems.put("COL1_COLOUR", COLOUR_ALERT_LOW);
        }

        // Set the price column to the holding value and colour
        // the column blue if we have no holdings
        if (pl_view && column1 == null) {
            rowItems.put("COL1_COLOUR", COLOUR_NA);
        }

        // Add currency symbol if we have a holding
        if (column1 != null) {
            rowItems
                    .put("COL1_VALUE", Tools.addCurrencySymbol(column1, symbol));
        }

        // Set the value and colour for the change values
        if (widgetSize == 1 || widgetSize == 3) {

            if (column3 != null) {
                if (pl_change) {
                    rowItems.put(
                            "COL3_VALUE",
                            Tools.addCurrencySymbol(column3, symbol));
                } else {
                    rowItems.put("COL3_VALUE", column3);
                }
                rowItems.put("COL3_COLOUR", getColourForChange(column3, prefs));
            }
            if (column4 != null) {
                rowItems.put("COL4_VALUE", column4);
                rowItems.put("COL4_COLOUR", getColourForChange(column4, prefs));
            }

        } else {
            if (column2 != null) {
                if (pl_change) {
                    rowItems.put(
                            "COL2_VALUE",
                            Tools.addCurrencySymbol(column2, symbol));
                } else {
                    rowItems.put("COL2_VALUE", column2);
                }
                rowItems.put("COL2_COLOUR", getColourForChange(column2, prefs));
            }
        }
        return rowItems;
    }

    // Format the volume to use K, M, B, or T suffix
    public static String formatVolume(String value) {

        Double volume = 0D;
        try {
            volume = Tools.parseDouble(value);
            if (volume > 999999999999D)
                value = String.format("%.0fT", volume / 1000000000000D);

            else if (volume > 999999999D)
                value = String.format("%.0fB", volume / 1000000000D);

            else if (volume > 999999D)
                value = String.format("%.0fM", volume / 1000000D);

            else if (volume > 999D)
                value = String.format("%.0fK", volume / 1000D);

            else
                value = String.format("%.0f", volume);

        } catch (Exception e) {
        }

        return value;
    }

    public static int getColourForChange(String value, SharedPreferences prefs) {

        // Colour the percentage change
        // - Red if the price went down
        // - Gray if the price went unchanged
        // - Green if the price went up

        if (value.equals(""))
            value = "0";

        int colour = 0;
        if (value.indexOf('-') > -1) {
            colour = COLOUR_LOSS;

        } else if (value.equals("0.0%")
                || value.equals("0.00")
                || value.equals("0.000"))

            // Choose green or gray based on prefs
            if (prefs.getBoolean("green_zero", false))
                colour = COLOUR_GAIN;

            else
                colour = Color.GRAY;

        else
            colour = COLOUR_GAIN;

        return colour;
    }

    public static void setOnClickPendingIntents(
            Context context,
            int appWidgetId,
            RemoteViews views) {

        // Set an onClick handler on the 'widget_left' layout
        Intent left_intent = new Intent(context, Widget.class);
        left_intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        left_intent.setAction("LEFT");
        views.setOnClickPendingIntent(R.id.widget_left, PendingIntent
                .getBroadcast(context, appWidgetId, left_intent, 0));

        // Set an onClick handler on the 'widget_right' layout
        Intent right_intent = new Intent(context, Widget.class);
        right_intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        right_intent.setAction("RIGHT");
        views.setOnClickPendingIntent(R.id.widget_right, PendingIntent
                .getBroadcast(context, appWidgetId, right_intent, 0));
    }

    public static void update(Context context, int appWidgetId, int updateMode) {

        // Get widget SharedPreferences
        SharedPreferences prefs = Tools.getWidgetPrefs(context, appWidgetId);

        // Choose between two widget sizes
        int widgetSize = prefs.getInt("widgetSize", 0);

        // Get relevant RemoteViews
        RemoteViews views = getRemoteViews(context, prefs, widgetSize);

        // Get the array size for widgets
        int arraySize = 0;
        if (widgetSize == 0 || widgetSize == 1)
            arraySize = 4;

        else if (widgetSize == 2 || widgetSize == 3)
            arraySize = 10;

        // Hide any rows for smaller widgets
        displayRows(views, arraySize);

        boolean found = false;
        String[] stocks = new String[arraySize];
        for (int i = 0; i < arraySize; i++) {
            stocks[i] = prefs.getString("Stock" + (i + 1), "");
            if (!stocks[i].equals("")) {
                found = true;
            }
        }

        // Ensure widget is not empty
        if (!found) {
            stocks[0] = "^DJI";
        }

        // Retrieve portfolio stocks
        HashMap<String, HashMap<PortfolioField, String>> portfolioStockMap =
                UserData.getPortfolioStockMapForWidget(context, stocks);

        // Check if we have an empty portfolio
        boolean noPortfolio = portfolioStockMap.isEmpty();

        // Get widget view preferences
        HashMap<Integer, Boolean> enabledViews =
                new HashMap<Integer, Boolean>();
        enabledViews.put(
                VIEW_DAILY_PERCENT,
                prefs.getBoolean("show_percent_change", false)
                        && (widgetSize == 0 || widgetSize == 2));
        enabledViews.put(
                VIEW_DAILY_CHANGE,
                prefs.getBoolean("show_absolute_change", true));
        enabledViews.put(
                VIEW_PORTFOLIO_PERCENT,
                prefs.getBoolean("show_portfolio_change", false)
                        && (widgetSize == 0 || widgetSize == 2)
                        && !noPortfolio);
        enabledViews.put(
                VIEW_PORTFOLIO_CHANGE,
                prefs.getBoolean("show_portfolio_abs", false) && !noPortfolio);
        enabledViews.put(
                VIEW_PORTFOLIO_PERCENT_AER,
                prefs.getBoolean("show_portfolio_aer", false) && !noPortfolio);
        enabledViews.put(
                VIEW_PL_DAILY_PERCENT,
                prefs.getBoolean("show_profit_daily_change", false)
                        && (widgetSize == 0 || widgetSize == 2)
                        && !noPortfolio);
        enabledViews.put(
                VIEW_PL_DAILY_CHANGE,
                prefs.getBoolean("show_profit_daily_abs", false)
                        && !noPortfolio);
        enabledViews.put(
                VIEW_PL_PERCENT,
                prefs.getBoolean("show_profit_change", false)
                        && (widgetSize == 0 || widgetSize == 2)
                        && !noPortfolio);
        enabledViews.put(
                VIEW_PL_CHANGE,
                prefs.getBoolean("show_profit_abs", false) && !noPortfolio);
        enabledViews.put(
                VIEW_PL_PERCENT_AER,
                prefs.getBoolean("show_profit_aer", false) && !noPortfolio);

        // Check if we have any portfolio-less views
        boolean defaultViews =
                enabledViews.get(VIEW_DAILY_PERCENT)
                        && enabledViews.get(VIEW_DAILY_CHANGE);

        // Setup setOnClickPendingIntents
        setOnClickPendingIntents(context, appWidgetId, views);

        // Return here if we are requesting a portfolio view and we have
        // no portfolio items to report on
        if (updateMode == VIEW_CHANGE && noPortfolio && !defaultViews) {
            return;
        }

        // Retrieve the last shown widgetView from the preferences
        int widgetView = prefs.getInt("widgetView", 0);
        if (updateMode == VIEW_CHANGE) {
            widgetView += 1;
            widgetView = widgetView % 10;
        }

        // Skip views as relevant
        int count = 0;
        while (!enabledViews.get(widgetView)) {
            count += 1;

            widgetView += 1;
            widgetView = widgetView % 10;

            // Percent change as default view if none selected
            if (count > 10) {
                widgetView = 0;
                break;
            }
        }

        // Only bother to save if the widget view changed
        if (widgetView != prefs.getInt("widgetView", 0)) {
            SharedPreferences.Editor editor;
            editor = prefs.edit();
            editor.putInt("widgetView", widgetView);
            editor.commit();
        }

        // Use existing data if changing view or no network connection exists
        boolean noCache = false;
        if (updateMode == VIEW_UPDATE) {
            noCache = true;
        }

        DataSource dataSource = new DataSource();
        HashMap<String, HashMap<StockField, String>> quotes =
                dataSource.getStockData(context, stocks, noCache);
        String quotesTimeStamp = dataSource.getTimeStamp();

        // If we have stock data then update the widget view
        if (!quotes.isEmpty()) {

            // Clear existing widget appearance
            if (widgetSize == 1 || widgetSize == 3) {
                for (int i = 1; i < arraySize + 1; i++) {
                    for (int j = 1; j < 6; j++) {
                        views.setTextViewText(getStockViewId(i, j), "");
                    }
                }
            } else {
                for (int i = 1; i < arraySize + 1; i++) {
                    for (int j = 1; j < 4; j++) {
                        views.setTextViewText(getStockViewId(i, j), "");
                    }
                }
            }

            int line_no = 0;
            for (String symbol : stocks) {

                // Skip blank symbols
                if (symbol.equals("")) {
                    continue;
                }

                // Get the info for this quote
                HashMap<StockField, String> quoteInfo =
                        new HashMap<StockField, String>();
                quoteInfo = quotes.get(symbol);

                line_no++;

                HashMap<String, Object> formattedRow =
                        getFormattedRow(
                                symbol,
                                quoteInfo,
                                portfolioStockMap,
                                widgetView,
                                prefs,
                                widgetSize);

                // Values
                views.setTextViewText(
                        getStockViewId(line_no, 1),
                        bolden(prefs, (String) formattedRow.get("COL0_VALUE")));
                views.setTextViewText(
                        getStockViewId(line_no, 2),
                        bolden(prefs, (String) formattedRow.get("COL1_VALUE")));
                views.setTextViewText(
                        getStockViewId(line_no, 3),
                        bolden(prefs, (String) formattedRow.get("COL2_VALUE")));

                // Add the other values if we have a wide widget
                if (widgetSize == 1 || widgetSize == 3) {
                    views.setTextViewText(
                            getStockViewId(line_no, 4),
                            bolden(
                                    prefs,
                                    (String) formattedRow.get("COL3_VALUE")));
                    views.setTextViewText(
                            getStockViewId(line_no, 5),
                            bolden(
                                    prefs,
                                    (String) formattedRow.get("COL4_VALUE")));
                }

                // Colours
                views.setTextColor(
                        getStockViewId(line_no, 1),
                        (Integer) formattedRow.get("COL0_COLOUR"));
                if (!prefs.getBoolean("colours_on_prices", false)) {

                    // Narrow widget
                    if (widgetSize == 0 || widgetSize == 2) {
                        views.setTextColor(
                                getStockViewId(line_no, 2),
                                (Integer) formattedRow.get("COL1_COLOUR"));
                        views.setTextColor(
                                getStockViewId(line_no, 3),
                                (Integer) formattedRow.get("COL2_COLOUR"));
                    }
                    // Wide widget
                    else {
                        views.setTextColor(
                                getStockViewId(line_no, 2),
                                (Integer) formattedRow.get("COL1_COLOUR"));
                        views.setTextColor(
                                getStockViewId(line_no, 3),
                                (Integer) formattedRow.get("COL2_COLOUR"));
                        views.setTextColor(
                                getStockViewId(line_no, 4),
                                (Integer) formattedRow.get("COL3_COLOUR"));
                        views.setTextColor(
                                getStockViewId(line_no, 5),
                                (Integer) formattedRow.get("COL4_COLOUR"));
                    }

                } else {
                    // Narrow widget
                    if (widgetSize == 0 || widgetSize == 2) {
                        views.setTextColor(
                                getStockViewId(line_no, 3),
                                (Integer) formattedRow.get("COL1_COLOUR"));
                        views.setTextColor(
                                getStockViewId(line_no, 2),
                                (Integer) formattedRow.get("COL2_COLOUR"));
                    }
                    // Wide widget
                    else {
                        views.setTextColor(
                                getStockViewId(line_no, 2),
                                (Integer) formattedRow.get("COL4_COLOUR"));
                        views.setTextColor(
                                getStockViewId(line_no, 4),
                                (Integer) formattedRow.get("COL1_COLOUR"));
                        views.setTextColor(
                                getStockViewId(line_no, 5),
                                (Integer) formattedRow.get("COL1_COLOUR"));
                    }
                }
            }

            // Set footer display
            String updated_display =
                    prefs.getString("updated_display", "visible");
            if (updated_display.equals("remove")) {
                views.setViewVisibility(R.id.text_footer, View.GONE);

            } else if (updated_display.equals("invisible")) {
                views.setViewVisibility(R.id.text_footer, View.INVISIBLE);

            } else {
                views.setViewVisibility(R.id.text_footer, View.VISIBLE);

                // Set footer text and colour
                String updated_colour =
                        prefs.getString("updated_colour", "light");
                int footer_colour = Color.parseColor("#555555");
                if (updated_colour.equals("light")) {
                    footer_colour = Color.GRAY;
                } else if (updated_colour.equals("yellow")) {
                    footer_colour = Color.parseColor("#cccc77");
                }

                // Show short time if specified in preferences
                if (!prefs.getBoolean("short_time", false)) {

                    // Get current day and month
                    SimpleDateFormat formatter = new SimpleDateFormat("dd MMM");
                    String current_date =
                            formatter.format(new Date()).toUpperCase();

                    // Set default time as today
                    if (quotesTimeStamp.equals("")) {
                        quotesTimeStamp = "NO DATE SET";
                    }

                    // Check if we should use yesterdays date or todays time
                    String[] split_time = quotesTimeStamp.split(" ");
                    if ((split_time[0] + " " + split_time[1])
                            .equals(current_date)) {
                        quotesTimeStamp = split_time[2];
                    } else {
                        quotesTimeStamp = (split_time[0] + " " + split_time[1]);
                    }
                }

                // Set time stamp
                views.setTextViewText(
                        R.id.text5,
                        bolden(prefs, quotesTimeStamp));
                views.setTextColor(R.id.text5, footer_colour);

                // Set the widget view text in the footer
                String currentViewText = "";

                if (widgetSize == 0 || widgetSize == 2) {

                    switch (widgetView) {
                        case VIEW_DAILY_PERCENT:
                            currentViewText = "";
                            break;

                        case VIEW_DAILY_CHANGE:
                            currentViewText = "DA";
                            break;

                        case VIEW_PORTFOLIO_PERCENT:
                            currentViewText = "PF T%";
                            break;

                        case VIEW_PORTFOLIO_CHANGE:
                            currentViewText = "PF TA";
                            break;

                        case VIEW_PORTFOLIO_PERCENT_AER:
                            currentViewText = "PF AER";
                            break;

                        case VIEW_PL_DAILY_PERCENT:
                            currentViewText = "P/L D%";
                            break;

                        case VIEW_PL_DAILY_CHANGE:
                            currentViewText = "P/L DA";
                            break;

                        case VIEW_PL_PERCENT:
                            currentViewText = "P/L T%";
                            break;

                        case VIEW_PL_CHANGE:
                            currentViewText = "P/L TA";
                            break;

                        case VIEW_PL_PERCENT_AER:
                            currentViewText = "P/L AER";
                            break;
                    }

                } else {
                    switch (widgetView) {
                        case VIEW_DAILY_PERCENT:
                            currentViewText = "";
                            break;

                        case VIEW_DAILY_CHANGE:
                            currentViewText = "";
                            break;

                        case VIEW_PORTFOLIO_PERCENT:
                            currentViewText = "PF T";
                            break;

                        case VIEW_PORTFOLIO_CHANGE:
                            currentViewText = "PF T";
                            break;

                        case VIEW_PORTFOLIO_PERCENT_AER:
                            currentViewText = "PF AER";
                            break;

                        case VIEW_PL_DAILY_PERCENT:
                            currentViewText = "P/L D";
                            break;

                        case VIEW_PL_DAILY_CHANGE:
                            currentViewText = "P/L D";
                            break;

                        case VIEW_PL_PERCENT:
                            currentViewText = "P/L T";
                            break;

                        case VIEW_PL_CHANGE:
                            currentViewText = "P/L T";
                            break;

                        case VIEW_PL_PERCENT_AER:
                            currentViewText = "P/L AER";
                            break;
                    }
                }

                // Update the view name and view name separator
                views.setTextViewText(
                        R.id.text6,
                        bolden(prefs, currentViewText));
                views.setTextColor(R.id.text6, footer_colour);
            }

            // Finally update the widget with the RemoteViews
            AppWidgetManager.getInstance(context).updateAppWidget(
                    appWidgetId,
                    views);
        }
    }

    public static void updateWidgets(Context context, int updateMode) {

        for (int appWidgetId : UserData.getAppWidgetIds2(context))
            WidgetBase.update(context, appWidgetId, updateMode);

        // Update last update time
        SharedPreferences prefs = Tools.getAppPrefs(context);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        Editor editor = prefs.edit();
        editor.putString("last_update1", formatter.format(new Date()));
        editor.commit();

        // Reset the alarm
        updateAlarmManager(context);
    }

    public static void doScheduledUpdates(Context context) {
        /*
         * Update the widget if allowed by the update schedule
		 */

        boolean doUpdates = true;
        SharedPreferences prefs = Tools.getAppPrefs(context);

        // Only update after start time
        String startTime = prefs.getString("update_start", null);
        if (startTime != null && !startTime.equals("")) {

            if (Tools.compareToNow(Tools.parseSimpleDate(startTime)) == 1)
                doUpdates = false;
        }

        // Only update before end time
        String endTime = prefs.getString("update_end", null);
        if (endTime != null && !endTime.equals("")) {

            if (Tools.compareToNow(Tools.parseSimpleDate(endTime)) == -1)
                doUpdates = false;
        }

        // Do not update on weekends
        Boolean update_weekend = prefs.getBoolean("update_weekend", false);
        if (!update_weekend) {
            int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
            if (dayOfWeek == 1 || dayOfWeek == 7)
                doUpdates = false;
        }

        // Perform updates as appropriate
        if (doUpdates)
            updateWidgets(context, VIEW_UPDATE);

        else
            updateWidgets(context, VIEW_NO_UPDATE);
    }

    public static void updateAlarmManager(Context context) {

        // Get the alarm service
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // Cancel existing alarms
        Intent intent = new Intent(ALARM_UPDATE);
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        context.getApplicationContext(),
                        0,
                        intent,
                        0);
        alarmManager.cancel(pendingIntent);

        // Get the configured update interval (default 30 minutes)
        SharedPreferences prefs = Tools.getAppPrefs(context);
        Long interval =
                Long.parseLong((prefs.getString(
                        "update_interval",
                        Long.toString(AlarmManager.INTERVAL_HALF_HOUR))));

        // First update delay
        Double firstInterval = interval.doubleValue();

        // Get the last update time
        String lastUpdate = prefs.getString("last_update1", null);
        Double elapsed = Tools.elapsedTime(lastUpdate, "yyyy-MM-dd HH:mm");

        // If the elapsed time is greater than the interval, then update now
        // otherwise, work out how much longer until the next update
        if (elapsed > 0)
            firstInterval = Math.max(interval - elapsed, 0);

        // Set the manager up with the specified interval
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.add(Calendar.MILLISECOND, firstInterval.intValue());
        alarmManager.setInexactRepeating(
                AlarmManager.RTC,
                calendar.getTimeInMillis(),
                interval,
                pendingIntent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();

        // Update all widgets if requested
        if (ALARM_UPDATE.equals(action)) {
            doScheduledUpdates(context);
        } else if (action.equals("LEFT")
                || action.equals("RIGHT")
                || action == null) {

            Bundle extras = intent.getExtras();
            if (extras != null) {
                int appWidgetId =
                        extras.getInt(
                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                AppWidgetManager.INVALID_APPWIDGET_ID);

                // Bring up the preferences screen if the user clicked
                // on the left side of the widget
                if (!action.equals("RIGHT")) {

                    Preferences.mAppWidgetId = appWidgetId;
                    Intent activity = new Intent(context, Preferences.class);
                    activity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(activity);

                }
                // Update the widget if the user clicked on the right
                // side of the widget
                else {
                    update(context, appWidgetId, VIEW_CHANGE);
                }

            }
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onUpdate(
            Context context,
            AppWidgetManager appWidgetManager,
            int[] appWidgetIds) {
        /*
         * Update the intersection of the appWidgetManager appWidgetIds and the
		 * true saved appWidgetIds
		 */

        // Reset alarm manager if needed
        updateAlarmManager(context);

        for (int i : UserData.getAppWidgetIds2(context))
            update(context, i, VIEW_NO_UPDATE);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);

        // Update the alarm manager
        updateAlarmManager(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);

        // Remove the appWidgetIds from our preferences
        for (int appWidgetId : appWidgetIds) {
            UserData.delAppWidgetId(context, appWidgetId);
        }

        // Cleanup preferences files
        UserData.cleanupPreferenceFiles(context);

        // Stop the AlarmManager if there are no more widgetIds
        if (UserData.getAppWidgetIds2(context).length == 0) {

            AlarmManager alarmManager =
                    (AlarmManager) context
                            .getSystemService(Context.ALARM_SERVICE);
            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(context, 0, new Intent(
                            ALARM_UPDATE), 0);
            alarmManager.cancel(pendingIntent);
        }
    }
}
