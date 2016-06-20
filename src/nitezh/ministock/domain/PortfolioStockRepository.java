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
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nitezh.ministock.DialogTools;
import nitezh.ministock.Storage;
import nitezh.ministock.UserData;
import nitezh.ministock.utils.Cache;
import nitezh.ministock.utils.CurrencyTools;
import nitezh.ministock.utils.NumberTools;

public class PortfolioStockRepository {

    public static final String PORTFOLIO_JSON = "portfolioJson.txt";
    public static final String WIDGET_JSON = "widgetJson";
    public HashMap<String, StockQuote> stocksQuotes = new HashMap<>();

            public HashMap<String, PortfolioStock> portfolioStocksInfo = new HashMap<>();
            public Set<String> widgetsStockSymbols = new HashSet<>();

            private static final HashMap<String, PortfolioStock> mPortfolioStocks = new HashMap<>();
            private static boolean mDirtyPortfolioStockMap = true;
            private Storage mAppStorage;

            public PortfolioStockRepository(Storage appStorage, Cache cache, WidgetRepository widgetRepository) {
                this.mAppStorage = appStorage;

                this.widgetsStockSymbols = widgetRepository.getWidgetsStockSymbols();
                this.portfolioStocksInfo = getPortfolioStocksInfo(widgetsStockSymbols);
                this.stocksQuotes = getStocksQuotes(appStorage, cache, widgetRepository);
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

    // Problem with empty stocks in portofolio

    public void backupPortfolio(Context context) {

        // Convert current portfolioStockInfo to JSONO-Object
        persist();

        // Write JSONO-Object to internal app storage
        this.mAppStorage.putString(PORTFOLIO_JSON, getStocksJson().toString()).apply();

        String rawJson = this.mAppStorage.getString(PORTFOLIO_JSON, "");
        UserData.writeExternalStorage(context, rawJson, PORTFOLIO_JSON);
        DialogTools.showSimpleDialog(context, "PortfolioActivity backed up",
               "Your portfolio settings have been backed up to ministocks/portfolioJson.txt");
    }

   /*
    *  prblem with getstocks() -> getStocksJson() storage
    */
    public void restorePortfolio(Context context) {
        mDirtyPortfolioStockMap = true;

        String rawJson = UserData.readExternalStorage(context, PORTFOLIO_JSON);

        this.mAppStorage.putString(PORTFOLIO_JSON, rawJson).apply();

        JSONObject test = getStocksJson();

        HashMap<String, PortfolioStock> stocksFromBackup = getStocksFromJson(test);

        //this.portfolioStocksInfo.clear();

        Iterator<String> it = stocksFromBackup.keySet().iterator();

        int i = 0;


        while (it.hasNext()) {

            String tmp = it.next();

            i++;

            this.mAppStorage.putString("Stock" + i, stocksFromBackup.get(tmp).getSymbol());

            this.mAppStorage.putString("Stock" + i + "_summary", "No description");

            PortfolioStock portfolioStock  = stocksFromBackup.get(tmp);

            updateStock(tmp, portfolioStock.getPrice(), portfolioStock.getDate(), portfolioStock.getQuantity(),
                    portfolioStock.getHighLimit(), portfolioStock.getLowLimit(), portfolioStock.getCustomName());

        }

        this.persist();


        if (rawJson == null) 
            DialogTools.showSimpleDialog(context, "Restore portfolio failed", "Backup file portfolioJson.txt not found");

        else 
            DialogTools.showSimpleDialog(context, "PortfolioActivity restored",
                "Your portfolio settings have been restored from ministocks/portfolioJson.txt");
    }


    public JSONObject getStocksJson() {
        JSONObject stocksJson = new JSONObject();
        try {
            stocksJson = new JSONObject(this.mAppStorage.getString(PORTFOLIO_JSON, ""));
        } catch (JSONException ignored) {
        }
        return stocksJson;
    }

     /*
      * Transform a JSONObject into a HashMap.
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
        for (String symbol : this.portfolioStocksInfo.keySet()) {
            String price = this.portfolioStocksInfo.get(symbol).getPrice();
            if ((price == null || price.equals("")) && !this.widgetsStockSymbols.contains(symbol)) {
                this.portfolioStocksInfo.remove(symbol);
            }
        }
    }

    public void saveChanges() {
        this.removeUnused();
        this.persist();
    }

    public enum PortfolioField {
        PRICE, DATE, QUANTITY, LIMIT_HIGH, LIMIT_LOW, CUSTOM_DISPLAY, SYMBOL_2
    }
}
