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

package nitezh.ministock.domain;

import android.content.Context;
import android.os.AsyncTask;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nitezh.ministock.DialogTools;
import nitezh.ministock.Storage;
import nitezh.ministock.SymbolProvider;
import nitezh.ministock.UserData;
import nitezh.ministock.activities.PreferencesActivity;
import nitezh.ministock.utils.Cache;
import nitezh.ministock.utils.CurrencyTools;
import nitezh.ministock.utils.NumberTools;

public class PortfolioStockRepository {
    public static final String PORTFOLIO_JSON = "portfolioJson.txt";
    public static final String WIDGET_JSON = "widgetJson";
    public HashMap<String, StockQuote> stocksQuotes = new HashMap<>();

    public HashMap<String, PortfolioStock> portfolioStocksInfo = new HashMap<>();
    public Set<String> widgetsStockSymbols = new HashSet<>();

    private static HashMap<String, PortfolioStock> mPortfolioStocks = new HashMap<>();
    private static boolean mDirtyPortfolioStockMap = true;
    private Storage mAppStorage;

    private static Set<String> widgetStocks;
    private Context context = null;


    public PortfolioStockRepository(Storage appStorage, Cache cache, WidgetRepository widgetRepository) {
        this.mAppStorage = appStorage;

        this.widgetsStockSymbols = widgetRepository.getWidgetsStockSymbols();
        this.portfolioStocksInfo = getPortfolioStocksInfo(widgetsStockSymbols);
        this.stocksQuotes = getStocksQuotes(mAppStorage, cache, widgetRepository);

        this.widgetStocks = widgetsStockSymbols;
    }

    public PortfolioStockRepository(Context context, Storage appStorage, Cache cache, WidgetRepository widgetRepository) {
        this.context = context;
        this.mAppStorage = appStorage;

        this.widgetsStockSymbols = widgetRepository.getWidgetsStockSymbols();
        this.portfolioStocksInfo = getPortfolioStocksInfo(widgetsStockSymbols);
        this.stocksQuotes = getStocksQuotes(mAppStorage, cache, widgetRepository);

    }


    private HashMap<String, StockQuote> getStocksQuotes(Storage appStorage, Cache cache, WidgetRepository widgetRepository) {
        Set<String> symbolSet = portfolioStocksInfo.keySet();

        return new StockQuoteRepository(appStorage, cache, widgetRepository)
                .getQuotes(Arrays.asList(symbolSet.toArray(new String[symbolSet.size()])), false);
    }

    private HashMap<String, PortfolioStock> getPortfolioStocksInfo(Set<String> symbols) {
        HashMap<String, PortfolioStock> stocks = this.getStocks();
        for (String symbol : symbols) {
            if (!stocks.containsKey(symbol)) {
                stocks.put(symbol, null);
            }
        }
        return stocks;
    }

    public List<Map<String, String>> getDisplayInfo() {
        NumberFormat numberFormat = NumberFormat.getInstance();

        List<Map<String, String>> info = new ArrayList<>();
        for (String symbol : this.getSortedSymbols()) {
            StockQuote quote = this.stocksQuotes.get(symbol);
            PortfolioStock stock = this.getStock(symbol);
            Map<String, String> itemInfo = new HashMap<>();

            populateDisplayNames(quote, stock, itemInfo);

            // Get the current price if we have the data
            String currentPrice = populateDisplayCurrentPrice(quote, itemInfo);

            if (hasInfoForStock(stock)) {
                String buyPrice = stock.getPrice();

                itemInfo.put("buyPrice", buyPrice);
                itemInfo.put("date", stock.getDate());

                populateDisplayHighLimit(stock, itemInfo);
                populateDisplayLowLimit(stock, itemInfo);

                itemInfo.put("quantity", stock.getQuantity());

                populateDisplayLastChange(numberFormat, symbol, quote, stock, itemInfo);
                populateDisplayTotalChange(numberFormat, symbol, stock, itemInfo, currentPrice, buyPrice);
                populateDisplayHoldingValue(numberFormat, symbol, stock, itemInfo, currentPrice);
            }
            itemInfo.put("symbol", symbol);
            info.add(itemInfo);
        }

        return info;
    }


    private boolean hasInfoForStock(PortfolioStock stock) {
        return !stock.getPrice().equals("");
    }

    private void populateDisplayHighLimit(PortfolioStock stock, Map<String, String> itemInfo) {
        String limitHigh = NumberTools.decimalPlaceFormat(stock.getHighLimit());
        itemInfo.put("limitHigh", limitHigh);
    }

    private void populateDisplayLowLimit(PortfolioStock stock, Map<String, String> itemInfo) {
        String limitLow = NumberTools.decimalPlaceFormat(stock.getLowLimit());
        itemInfo.put("limitLow", limitLow);
    }

    private void populateDisplayHoldingValue(NumberFormat numberFormat, String symbol, PortfolioStock stock, Map<String, String> itemInfo, String currentPrice) {
        String holdingValue = "";
        try {
            Double holdingQuanta = NumberTools.parseDouble(stock.getQuantity());
            Double holdingPrice = numberFormat.parse(currentPrice).doubleValue();
            holdingValue = CurrencyTools.addCurrencyToSymbol(String.format("%.0f", (holdingQuanta * holdingPrice)), symbol);
        } catch (Exception ignored) {
        }
        itemInfo.put("holdingValue", holdingValue);
    }

    private void populateDisplayLastChange(NumberFormat numberFormat, String symbol, StockQuote quote, PortfolioStock stock, Map<String, String> itemInfo) {
        String lastChange = "";
        try {
            if (quote != null) {
                lastChange = quote.getPercent();
                try {
                    Double change = numberFormat.parse(quote.getChange()).doubleValue();
                    Double totalChange = NumberTools.parseDouble(stock.getQuantity()) * change;
                    lastChange += " / " + CurrencyTools.addCurrencyToSymbol(String.format("%.0f", (totalChange)), symbol);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        itemInfo.put("lastChange", lastChange);
    }

    private void populateDisplayTotalChange(NumberFormat numberFormat, String symbol, PortfolioStock stock, Map<String, String> itemInfo, String currentPrice, String buyPrice) {
        // Calculate total change, including percentage
        String totalChange = "";
        try {
            Double price = numberFormat.parse(currentPrice).doubleValue();
            Double buy = Double.parseDouble(buyPrice);
            Double totalPercentChange = price - buy;
            totalChange = String.format("%.0f", 100 * totalPercentChange / buy) + "%";

            // Calculate change
            try {
                Double quanta = NumberTools.parseDouble(stock.getQuantity());
                totalChange += " / " + CurrencyTools.addCurrencyToSymbol(String.format("%.0f", quanta * totalPercentChange), symbol);
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
        itemInfo.put("totalChange", totalChange);
    }

    private String populateDisplayCurrentPrice(StockQuote quote, Map<String, String> itemInfo) {
        String currentPrice = "";
        if (quote != null)
            currentPrice = quote.getPrice();
        itemInfo.put("currentPrice", currentPrice);

        return currentPrice;
    }

    private void populateDisplayNames(StockQuote quote, PortfolioStock stock, Map<String, String> itemInfo) {
        String name = "No description";
        if (quote != null) {
            if (!stock.getCustomName().equals("")) {
                name = stock.getCustomName();
                itemInfo.put("customName", name);
            }
            if (name.equals("")) {
                name = quote.getName();
            }
        }
        itemInfo.put("name", name);
    }

    private PortfolioStock getStock(String symbol) {
        PortfolioStock stock = this.portfolioStocksInfo.get(symbol);
        if (stock == null) {
            stock = new PortfolioStock(symbol, "", "", "", "", "", "", null);
        }
        this.portfolioStocksInfo.put(symbol, stock);

        return stock;
    }

    /**
     *  Write your current portfolio to a file named with parameter file name.
     *  Fetch portfolio as JsonObject from internal Storage and write it to external Storage as String.
     *
     * @param context display dialogs for User.
     * @param fileName name the file for your backup.
     */

    public void backupPortfolio(Context context, String fileName) {
        // Restrict filenames to non-special characters
        if(!fileName.matches("[a-zA-Z0-9_\\- ]*")) {
            System.out.println(fileName);
            DialogTools.showSimpleDialogOk(context, "Backup failed",
                    "Only letters, numbers, whitespaces and _ or - are allowed in file names");
            return;
        }

        // Convert current portfolioStockInfo to JSON-Object
        persist();

        // Write JSON-Object as String to internal app storage
        this.mAppStorage.putString(PORTFOLIO_JSON, getStocksJson().toString()).apply();

        String rawJson = this.mAppStorage.getString(PORTFOLIO_JSON, "");

        // Write backup as String.
        UserData.writeExternalStorage(context, rawJson, fileName + ".txt", "portfoliobackups/");

        // Inform User about backup status.
        DialogTools.showSimpleDialog(context, "PortfolioActivity backed up",
               "Your portfolio settings have been backed up to ministocks/portfoliobackups/"+ fileName);
    }


    /**
     *  Transform String from backup file to JsonObject with stock information.
     *  Create a portfolioStocks map from JsonObject.
     *
     *
     * @param context Show Dialog for User feedback.
     * @param backupName name the file you want to get backup data from.
     */

    public void restorePortfolio(Context context, String backupName) {
        mDirtyPortfolioStockMap = true;

        //this.portfolioStocksInfo.clear();


        // Get String from backup file
        String rawJson = UserData.readExternalStorage(context, backupName, "portfoliobackups/");

        // Save current portfolio to internal Storage.
        this.mAppStorage.putString(PORTFOLIO_JSON, rawJson).apply();

        // Create JSonObject from internalstorage.
        JSONObject portfolioJSon = getStocksJson();

        // Create portfolio stocks from JsonObject.
        HashMap<String, PortfolioStock> stocksFromBackup = getStocksFromJson(portfolioJSon);

        Iterator<String> it = stocksFromBackup.keySet().iterator();


        //
        int i = 0;
        while (it.hasNext()) {
            String tmp = it.next();
            i++;

            PortfolioStock portfolioStock = stocksFromBackup.get(tmp);
            portfolioStocksInfo.put(stocksFromBackup.get(tmp).getSymbol(), portfolioStock);


            /*
            String symbol = stocksFromBackup.get(tmp).getSymbol();
            String customName = stocksFromBackup.get(tmp).getCustomName();

            try {
                this.mAppStorage.putString("Stock" + i, symbol);
                if(!customName.equals("")) {
                    this.mAppStorage.putString("Stock" + i + "_summary", customName);
                } else {
                    this.mAppStorage.putString("Stock" + i + "_summary", this.getBackupDescription(symbol));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        */
        }


        // Put restored portfolio into map -> Constructor restores portfolio from mPortfolioStocks.
        mPortfolioStocks = portfolioStocksInfo;
        this.persist();
        // Constructor should load portfolio from mPortfolioStocks.
        mDirtyPortfolioStockMap = false;

        if (rawJson == null)
            // Dialog for failure
            DialogTools.showSimpleDialog(context, "Restore portfolio failed", "Backup file not found");

        else
            // Show Dialog for success and which backup has been rerstored.
            DialogTools.showSimpleDialog(context, "PortfolioActivity restored",
                    "Your portfolio settings have been restored from ministocks/portfoliobackups/" + backupName);
    }

    public JSONObject getStocksJson() {
        JSONObject stocksJson = new JSONObject();
        try {
            stocksJson = new JSONObject(this.mAppStorage.getString(PORTFOLIO_JSON, ""));
        } catch (JSONException ignored) {
        }
        return stocksJson;
    }

    /**
     * Part from method getStocks()
     * Modified for transformation from JsonObject to HashMap.
     *
     * @param json JSonObject with information about stocks and portfolio.
     * @return HashMap with Portfolio and Stocks information created from json.
     */
    public HashMap<String, PortfolioStock> getStocksFromJson(JSONObject json) {

        Iterator keys;
        keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next().toString();
            JSONObject itemJson = new JSONObject();
            try {
                itemJson = json.getJSONObject(key);
            } catch (JSONException ignored) {
            }

            HashMap<PortfolioField, String> stockInfoMap = new HashMap<>();
            for (PortfolioField f : PortfolioField.values()) {
                String data = "";
                try {
                    if (!itemJson.get(f.name()).equals("empty")) {
                        data = itemJson.get(f.name()).toString();
                    }
                } catch (JSONException ignored) {
                }
                stockInfoMap.put(f, data);
            }

            PortfolioStock stock = new PortfolioStock(key,
                    stockInfoMap.get(PortfolioField.PRICE),
                    stockInfoMap.get(PortfolioField.DATE),
                    stockInfoMap.get(PortfolioField.QUANTITY),
                    stockInfoMap.get(PortfolioField.LIMIT_HIGH),
                    stockInfoMap.get(PortfolioField.LIMIT_LOW),
                    stockInfoMap.get(PortfolioField.CUSTOM_DISPLAY),
                    stockInfoMap.get(PortfolioField.SYMBOL_2));
            mPortfolioStocks.put(key, stock);
        }
        mDirtyPortfolioStockMap = false;

        return mPortfolioStocks;
    }


    public HashMap<String, PortfolioStock> getStocks() {
        if (!mDirtyPortfolioStockMap) {
            return mPortfolioStocks;
        }
        mPortfolioStocks.clear();

        // Use the Json data if present
        Iterator keys;
        JSONObject json = this.getStocksJson();
        keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next().toString();
            JSONObject itemJson = new JSONObject();
            try {
                itemJson = json.getJSONObject(key);
            } catch (JSONException ignored) {
            }

            HashMap<PortfolioField, String> stockInfoMap = new HashMap<>();
            for (PortfolioField f : PortfolioField.values()) {
                String data = "";
                try {
                    if (!itemJson.get(f.name()).equals("empty")) {
                        data = itemJson.get(f.name()).toString();
                    }
                } catch (JSONException ignored) {
                }
                stockInfoMap.put(f, data);
            }

            PortfolioStock stock = new PortfolioStock(key,
                    stockInfoMap.get(PortfolioField.PRICE),
                    stockInfoMap.get(PortfolioField.DATE),
                    stockInfoMap.get(PortfolioField.QUANTITY),
                    stockInfoMap.get(PortfolioField.LIMIT_HIGH),
                    stockInfoMap.get(PortfolioField.LIMIT_LOW),
                    stockInfoMap.get(PortfolioField.CUSTOM_DISPLAY),
                    stockInfoMap.get(PortfolioField.SYMBOL_2));
            mPortfolioStocks.put(key, stock);
        }
        mDirtyPortfolioStockMap = false;

        return mPortfolioStocks;
    }


    public void persist() {
        JSONObject json = new JSONObject();
        for (String symbol : this.portfolioStocksInfo.keySet()) {
            if (this.portfolioStocksInfo.get(symbol) == null) {
                updateStock(symbol);
            }

            PortfolioStock item = this.portfolioStocksInfo.get(symbol);
            if (!item.isEmpty()) {
                try {
                    json.put(symbol, item.toJson());
                } catch (JSONException ignored) {
                }
            }
        }

        this.mAppStorage.putString(PORTFOLIO_JSON, json.toString());
        this.mAppStorage.apply();
        mDirtyPortfolioStockMap = true;
    }

    public HashMap<String, PortfolioStock> getStocksForSymbols(List<String> symbols) {
        HashMap<String, PortfolioStock> stocksForSymbols = new HashMap<>();
        HashMap<String, PortfolioStock> stocks = this.getStocks();

        for (String symbol : symbols) {
            PortfolioStock stock = stocks.get(symbol);
            if (stock != null && !stock.isEmpty()) {
                stocksForSymbols.put(symbol, stock);
            }
        }

        return stocksForSymbols;
    }

    public List<String> getSortedSymbols() {
        ArrayList<String> symbols = new ArrayList<>();
        for (String key : this.portfolioStocksInfo.keySet()) {
            symbols.add(key);
        }

        try {
            // Ensure symbols beginning with ^ appear first
            Collections.sort(symbols, new RuleBasedCollator("< '^' < a"));
        } catch (ParseException ignored) {
        }
        return symbols;
    }

    public void updateStock(String symbol, String price, String date, String quantity,
                            String limitHigh, String limitLow, String customDisplay) {
        PortfolioStock portfolioStock = new PortfolioStock(symbol, price, date, quantity,
                limitHigh, limitLow, customDisplay, null);
        this.portfolioStocksInfo.put(symbol, portfolioStock);
    }

    public void updateStock(String symbol) {
        this.updateStock(symbol, "", "", "", "", "", "");
    }

    public void removeUnused() {
        try {
            for (String symbol : this.portfolioStocksInfo.keySet()) {
                String price = this.portfolioStocksInfo.get(symbol).getPrice();
                if ((price == null /* || price.equals("")*/) && !this.widgetsStockSymbols.contains(symbol)) {
                    this.portfolioStocksInfo.remove(symbol);
                }
            }
        } catch (ConcurrentModificationException e) {
            e.printStackTrace();
        }
    }

    public void saveChanges() {
        this.removeUnused();
        this.persist();
    }

    public enum PortfolioField {
        PRICE, DATE, QUANTITY, LIMIT_HIGH, LIMIT_LOW, CUSTOM_DISPLAY, SYMBOL_2
    }


    /**
     * Gets the description for stocks after restoring a backup
     */
    private class Description extends AsyncTask<String, Void , String> {
        protected String doInBackground(String... query){
            String symbol = query[0];
            try {
                return SymbolProvider.getDescription(symbol);
            }catch (Exception e){
                DialogTools.showSimpleDialog(context,"Sorry","An unexpected error has occurred");
            }

            return "";
        }

        protected void onProgressUpdate(Integer... progress) {
            DialogTools.showSimpleDialog(context, "Please Wait", "Backup in progress");
        }
    }

    /**
     * Find description for input symbol.
     * Used for restore backup. Write description to stocks setup.
     *
     * @param symbol you need a description for.
     * @return description for symbol. Empty description, if it gets no description back.
     */
    private String getBackupDescription(String symbol){
        try {
            return new Description().execute(symbol).get();
        } catch (Exception ignored) {
        }
        return "No description";
    }

    /**
     *  Create a simple backup file, where you just store symbols for backuped stocks.
     *
     * @param context needed for external Storage.
     * @param fileName define name for your backup.
     */
    public void backupWidget(Context context, String fileName) {
        // Restrict filenames to non-special characters
        if(!fileName.matches("[a-zA-Z0-9_\\- ]*")) {
            System.out.println(fileName);
            DialogTools.showSimpleDialogOk(context, "Backup failed",
                    "Only letters, numbers, whitespaces and _ or - are allowed in file names");
            return;
        }

        int widgetSize = this.mAppStorage.getInt("widgetSize", 0);

        int maxStocks = PreferencesActivity.MAX_STOCKS;

        // Storage for backup file.
        ArrayList<String> backupStocks = new ArrayList<>();

        // Header for backup, where widgetsize is stored as identifier for widgets.
        String tmp = "Widgetsize: " + widgetSize + "\n";

        backupStocks.add(tmp);
        int i = 0;

        // Create backup file with symbols from internal storage.
        while (i < maxStocks) {
            i++;

            tmp = this.mAppStorage.getString("Stock" + i, "");

            if (!tmp.equals(""))
                backupStocks.add("Stock" + i + ": " + tmp + "\n");

        }

        // Write backup to external storage.
        UserData.writeExternalStorage(context, backupStocks.toString(), fileName + ".txt", "widgetbackups");
            DialogTools.showSimpleDialog(context, "Widget backup successful.",
                    "Your widget has been backed up to ministocks/widgetbackups/" + fileName);
    }

    /**
     * Read external storage and parse backup file.
     * Problems can occur, if backup file has not same format like backup file or
     * same problems happen, if someone played with backup file.
     *
     * @param context needed for dialog. Display feedback for User.
     * @param fileName Name of backup, that should be restored.
     */
    public void restoreWidget(Context context, String fileName) {

        int widgetsize = this.mAppStorage.getInt("widgetSize", 0);

        String rawJson = UserData.readExternalStorage(context, fileName, "widgetbackups");

        // Parse backup file and transform into an array.
        String delims = "[:\n]+";
        String[] tokens = null;

        try {
            tokens = rawJson.split(delims);
        } catch (NullPointerException ignored) {
        }

        // Check for correct widgetsize, else print error.
        if (tokens != null && tokens[1].trim().equals(String.valueOf(widgetsize))) {
            // Remove all stocks from widget.
            for (int j = 1; j < 16; j++) {
                this.mAppStorage.putString("Stock" + j, "").apply();
                this.mAppStorage.putString("Stock" + j + "_summary", "").apply();
            }

            int i = 1;

            // Write symbols to current widget.
            for (int j = 3; j < tokens.length; j += 2) {


                this.mAppStorage.putString("Stock" + i, tokens[j]).apply();
                this.mAppStorage.putString("Stock" + i + "_summary", getBackupDescription(tokens[j])).apply();
                i++;
            }
            DialogTools.showSimpleDialog(context, "Restore widget successful", "This widget has been successfully restored!");
        } else
            DialogTools.showSimpleDialog(context, "Restore widget failed", "This widget has the wrong size for this backup!");

    }
}
