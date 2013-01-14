package com.jbooktrader.strategy;


import com.jbooktrader.indicator.balance.*;
import com.jbooktrader.indicator.price.*;
import com.jbooktrader.platform.indicator.*;
import com.jbooktrader.platform.model.*;
import com.jbooktrader.platform.optimizer.*;
import com.jbooktrader.strategy.base.*;



/**
 *
 */
public class SampleCL extends StrategyCL {

    // Technical indicators
    private Indicator balanceVelocityInd, priceVelocityInd;

    // Strategy parameters names
    private static final String PERIOD = "Period";
    private static final String SCALE = "Scale";
    private static final String ENTRY = "Entry";
    private static final String EXIT = "Exit";

    // Strategy parameters values
    private final int entry, exit, scale;


    public SampleCL(StrategyParams optimizationParams) throws JBookTraderException {
        super(optimizationParams);

        entry = getParam(ENTRY);
        exit = getParam(EXIT);
        scale = getParam(SCALE);
    }

    @Override
    public void setParams() {
        addParam(PERIOD, 2200, 3600, 5, 3515);
        addParam(SCALE, 5, 25, 1, 25);
        addParam(ENTRY, 55, 120, 1, 95);
        addParam(EXIT, -50, 0, 1, 0);
    }

    @Override
    public void setIndicators() {
        balanceVelocityInd = addIndicator(new BalanceVelocity(1, getParam(PERIOD)));
        priceVelocityInd = addIndicator(new PriceVelocity(1, getParam(PERIOD)));

    }

    @Override
    public void onBookSnapshot() {
        double balanceVelocity = balanceVelocityInd.getValue();
        double priceVelocity = priceVelocityInd.getValue();

        double force = balanceVelocity - scale * priceVelocity;
        if (force >= entry && balanceVelocity > 0 && priceVelocity < 0) {
            setPosition(1);
        } else if (force <= -exit) {
            setPosition(0);
        }
    }
}