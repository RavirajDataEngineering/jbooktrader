package com.jbooktrader.platform.optimizer;

import com.jbooktrader.platform.chart.*;
import com.jbooktrader.platform.dialog.*;
import com.jbooktrader.platform.marketbook.MarketSnapshotFilter;
import com.jbooktrader.platform.model.*;
import static com.jbooktrader.platform.optimizer.PerformanceMetric.*;
import static com.jbooktrader.platform.preferences.JBTPreferences.*;
import com.jbooktrader.platform.preferences.*;
import com.jbooktrader.platform.startup.*;
import com.jbooktrader.platform.strategy.*;
import com.jbooktrader.platform.util.*;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Dialog to specify options for back testing using a historical data file.
 */
public class OptimizerDialog extends JBTDialog {
    private static final Dimension MIN_SIZE = new Dimension(890, 500);// minimum frame size
    private final PreferencesHolder prefs;
    private final String strategyName;
    private JPanel progressPanel;
    private JButton cancelButton, optimizeButton, optimizationMapButton, closeButton, selectFileButton, advancedOptionsButton;
    private JTextField fileNameText, minTradesText;
    private JComboBox selectionCriteriaCombo, optimizationMethodCombo;
    private JLabel progressLabel;
    private JProgressBar progressBar;
    private JTable resultsTable;
    private TableColumnModel paramTableColumnModel;
    private TableColumn stepColumn;

    private ParamTableModel paramTableModel;
    private JTextField combinationField;
    private ResultsTableModel resultsTableModel;
    private Strategy strategy;
    private List<OptimizationResult> optimizationResults;

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
    private JFormattedTextField fromText, toText;
    private JCheckBox ignorePeriod;
    
    private OptimizerRunner optimizerRunner;

    public OptimizerDialog(JFrame parent, String strategyName) {
        super(parent);
        prefs = PreferencesHolder.getInstance();
        this.strategyName = strategyName;
        init();
        pack();
        assignListeners();
        setLocationRelativeTo(null);
        initParams();
    }

    public void setProgress(final long count, final long iterations, final String text, final String label) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                progressLabel.setText(label);
                int percent = (int) (100 * (count / (double) iterations));
                progressBar.setValue(percent);
                progressBar.setString(text + ": " + percent + "% completed");
            }
        });
    }

    public void setProgress(final long count, final long iterations, final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                int percent = (int) (100 * (count / (double) iterations));
                progressBar.setValue(percent);
                progressBar.setString(text + percent + "%");
            }
        });
    }

    public void enableProgress() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                progressLabel.setText("");
                progressBar.setValue(0);
                progressPanel.setVisible(true);
                optimizeButton.setEnabled(false);
                cancelButton.setEnabled(true);
                getRootPane().setDefaultButton(cancelButton);
            }
        });
    }

    public void showProgress(final String progressText) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                progressBar.setValue(0);
                progressBar.setString(progressText);

            }
        });
    }

    public void signalCompleted() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                progressPanel.setVisible(false);
                optimizeButton.setEnabled(true);
                cancelButton.setEnabled(false);
                getRootPane().setDefaultButton(optimizationMapButton);
            }
        });
    }

    private void setOptions() throws JBookTraderException {
        String historicalFileName = fileNameText.getText();

        File file = new File(historicalFileName);
        if (!file.exists()) {
            fileNameText.requestFocus();
            String msg = "Historical file " + "\"" + historicalFileName + "\"" + " does not exist.";
            throw new JBookTraderException(msg);
        }

        try {
            int minTrades = Integer.parseInt(minTradesText.getText());
            if (minTrades < 2) {
                minTradesText.requestFocus();
                throw new JBookTraderException("\"" + "Minimum trades" + "\"" + " must be greater or equal to 2.");
            }
        } catch (NumberFormatException nfe) {
            minTradesText.requestFocus();
            throw new JBookTraderException("\"" + "Minimum trades" + "\"" + " must be an integer.");
        }
    }

    private void setParamTableColumns() {
        int optimizationMethod = optimizationMethodCombo.getSelectedIndex();
        int columnCount = paramTableColumnModel.getColumnCount();
        if (optimizationMethod == 0) {
            if (columnCount == 3) {
                paramTableColumnModel.addColumn(stepColumn);
            }
        } else if (optimizationMethod == 1) {
            if (columnCount == 4) {
                paramTableColumnModel.removeColumn(stepColumn);
            }
        }
    }

    private void assignListeners() {
        optimizeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    prefs.set(BackTesterFileName, fileNameText.getText());
                    prefs.set(OptimizerMinTrades, minTradesText.getText());
                    prefs.set(OptimizerSelectBy, (String) selectionCriteriaCombo.getSelectedItem());
                    prefs.set(OptimizerMethod, (String) optimizationMethodCombo.getSelectedItem());
                    prefs.set(OptimizerTestingPeriodStart, fromText.getText());
                    prefs.set(OptimizerTestingPeriodEnd, toText.getText());
                    prefs.set(BackTesterTestingIgnorePeriod, (ignorePeriod.isSelected() ? "true" : "false"));
                    setOptions();
                    StrategyParams params = paramTableModel.getParams();

                    int optimizationMethod = optimizationMethodCombo.getSelectedIndex();
                    if (optimizationMethod == 0) {
                        optimizerRunner = new BruteForceOptimizerRunner(OptimizerDialog.this, strategy, params);
                    } else if (optimizationMethod == 1) {
                        optimizerRunner = new DivideAndConquerOptimizerRunner(OptimizerDialog.this, strategy, params);
                    }

                    new Thread(optimizerRunner).start();
                } catch (Exception ex) {
                    MessageDialog.showError(OptimizerDialog.this, ex);
                }
            }
        });
        
        ignorePeriod.addActionListener(new ActionListener() {
        	public void actionPerformed(ActionEvent e) {
        		try {
        			fromText.setEnabled(!ignorePeriod.isSelected());
        			toText.setEnabled(!ignorePeriod.isSelected());
        		}
        		catch (Exception ecb) {
                    MessageDialog.showError(OptimizerDialog.this, ecb);
        		}
        	}
        });

        optimizationMapButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    if (optimizationResults == null || optimizationResults.isEmpty()) {
                        MessageDialog.showMessage(OptimizerDialog.this, "No optimization results to map.");
                        return;
                    }

                    OptimizationMap optimizationMap = new OptimizationMap(OptimizerDialog.this, strategy,
                            optimizationResults, getSortCriteria());
                    JDialog chartFrame = optimizationMap.getChartFrame();
                    chartFrame.setVisible(true);
                } catch (Exception ex) {
                    MessageDialog.showError(OptimizerDialog.this, ex);
                }
            }
        });

        optimizationMethodCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setParamTableColumns();
            }
        });


        closeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (optimizerRunner != null) {
                    closeButton.setEnabled(false);
                    optimizerRunner.cancel();
                }
                dispose();
            }
        });

        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (optimizerRunner != null) {
                    cancelButton.setEnabled(false);
                    optimizerRunner.cancel();
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (optimizerRunner != null) {
                    optimizerRunner.cancel();
                }
                dispose();
            }
        });

        selectFileButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser(JBookTrader.getAppPath());
                fileChooser.setDialogTitle("Select Historical Data File");

                String filename = getFileName();
                if (filename.length() != 0) {
                    fileChooser.setSelectedFile(new File(filename));
                }

                if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    File file = fileChooser.getSelectedFile();
                    fileNameText.setText(file.getAbsolutePath());
                }
            }
        });
        
        paramTableModel.addTableModelListener(new TableModelListener() {
        	public void tableChanged(TableModelEvent e) {
        		if (e.getType() == TableModelEvent.UPDATE) {
        			// We ignore events from the combination field itself.
        			if (e.getSource() != combinationField) {
        				DecimalFormat df0 = NumberFormatterFactory.getNumberFormatter(0, true);
        				
        				// Get number of combinations and display then in the combinationField
        				combinationField.setText(df0.format(paramTableModel.getNumCombinations()) + " combinations");
        			}
        		}
        	}
        });
    }


    private void init() {
        setModal(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setTitle("Strategy Optimizer - " + strategyName);

        getContentPane().setLayout(new BorderLayout());

        JPanel northPanel = new JPanel(new SpringLayout());
        JPanel centerPanel = new JPanel(new SpringLayout());
        JPanel southPanel = new JPanel(new BorderLayout());

        // strategy panel and its components
        JPanel filenamePanel = new JPanel(new SpringLayout());

        JLabel fileNameLabel = new JLabel("Historical data file:", JLabel.TRAILING);
        fileNameText = new JTextField();
        fileNameText.setText(prefs.get(BackTesterFileName));
        selectFileButton = new JButton("...");
        selectFileButton.setPreferredSize(new Dimension(32, 32));

        fileNameLabel.setLabelFor(fileNameText);

        filenamePanel.add(fileNameLabel);
        filenamePanel.add(fileNameText);
        filenamePanel.add(selectFileButton);

        SpringUtilities.makeCompactGrid(filenamePanel, 1, 3, 0, 0, 12, 0);

        // date ranger filter panel
        // From field
        JPanel datePanel = new JPanel(new SpringLayout());
        ignorePeriod = new JCheckBox("Use all data", prefs.get(BackTesterTestingIgnorePeriod).equals("true"));
        datePanel.add(ignorePeriod);

        JLabel fromLabel = new JLabel("From:", JLabel.TRAILING);
        fromText = new JFormattedTextField(sdf);
        fromText.setText(prefs.get(OptimizerTestingPeriodStart));
        fromText.setToolTipText("This field is optional. Format is: "+DATE_FORMAT+". For example: " + sdf.format(new Date()));
        fromText.setEnabled(!ignorePeriod.isSelected());
        fromLabel.setLabelFor(fromText);
        datePanel.add(fromLabel);
        datePanel.add(fromText);
        // To field
        JLabel toLabel = new JLabel("To:", JLabel.TRAILING);
        toText = new JFormattedTextField(sdf);
        toText.setText(prefs.get(OptimizerTestingPeriodEnd));
        toText.setToolTipText("This field is optional. Format is: "+DATE_FORMAT+". For example: " + sdf.format(new Date()));
        toText.setEnabled(!ignorePeriod.isSelected());
        toLabel.setLabelFor(toText);
        datePanel.add(toLabel);
        datePanel.add(toText);
        // spring packing
        SpringUtilities.makeOneLineGrid(datePanel);
        
        // strategy parameters panel and its components
        JPanel strategyParamPanel = new JPanel(new SpringLayout());
        JScrollPane paramScrollPane = new JScrollPane();
        paramTableModel = new ParamTableModel();
        JTable paramTable = new JTable(paramTableModel);
        paramTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        paramTable.setShowGrid(false);

        paramTableColumnModel = paramTable.getColumnModel();
        stepColumn = paramTableColumnModel.getColumn(3);

        paramScrollPane.getViewport().add(paramTable);
        paramScrollPane.setPreferredSize(new Dimension(0, 95));

        combinationField = new JTextField("Calculating combinations...");
        combinationField.setBackground(fileNameLabel.getBackground());
        combinationField.setBorder(BorderFactory.createEmptyBorder());
        combinationField.setFont(new Font(Font.DIALOG, Font.ITALIC, 11));
        combinationField.setEditable(false);
        combinationField.setHorizontalAlignment(JTextField.RIGHT);
        combinationField.setFocusable(false);
        combinationField.setOpaque(false);
        
        strategyParamPanel.add(paramScrollPane);
        strategyParamPanel.add(combinationField);
        
        SpringUtilities.makeCompactGrid(strategyParamPanel, 2, 1, 0, 0, 12, 0);

        // optimization options panel and its components
        JPanel optimizationOptionsPanel = new JPanel(new SpringLayout());

        JLabel optimizationMethodLabel = new JLabel("Search method: ");
        optimizationMethodCombo = new JComboBox(new String[] {"Brute force", "Divide & Conquer"});
        String optimizerMethod = prefs.get(OptimizerMethod);
        if (optimizerMethod.length() > 0) {
            optimizationMethodCombo.setSelectedItem(optimizerMethod);
        }

        optimizationMethodLabel.setLabelFor(optimizationMethodCombo);
        optimizationOptionsPanel.add(optimizationMethodLabel);
        optimizationOptionsPanel.add(optimizationMethodCombo);

        JLabel selectionCriteriaLabel = new JLabel("Selection criteria: ");
        String[] sortFactors = new String[] {PF.getName(), NetProfit.getName(), Kelly.getName(), PI.getName()};
        selectionCriteriaCombo = new JComboBox(sortFactors);
        selectionCriteriaLabel.setLabelFor(selectionCriteriaCombo);
        optimizationOptionsPanel.add(selectionCriteriaLabel);
        optimizationOptionsPanel.add(selectionCriteriaCombo);

        String selectBy = prefs.get(OptimizerSelectBy);
        if (selectBy.length() > 0) {
            selectionCriteriaCombo.setSelectedItem(selectBy);
        }

        JLabel minTradesLabel = new JLabel("Minimum trades: ");
        minTradesText = new JTextField();

        minTradesText.setText(prefs.get(OptimizerMinTrades));
        minTradesLabel.setLabelFor(minTradesText);
        optimizationOptionsPanel.add(minTradesLabel);
        optimizationOptionsPanel.add(minTradesText);

        advancedOptionsButton = new JButton("Advanced...");
        optimizationOptionsPanel.add(advancedOptionsButton);
        advancedOptionsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new AdvancedOptimizationOptionsDialog((JFrame) OptimizerDialog.this.getParent());
            }
        });


        SpringUtilities.makeCompactGrid(optimizationOptionsPanel, 1, 7, 0, 0, 12, 0);

        northPanel.add(new TitledSeparator(new JLabel("Strategy parameters")));
        northPanel.add(filenamePanel);
        northPanel.add(datePanel);
        northPanel.add(strategyParamPanel);
        northPanel.add(new TitledSeparator(new JLabel("Optimization options")));
        northPanel.add(optimizationOptionsPanel);
        northPanel.add(new TitledSeparator(new JLabel("Optimization Results")));
        SpringUtilities.makeCompactGrid(northPanel, 7, 1, 12, 12, 0, 8);

        JScrollPane resultsScrollPane = new JScrollPane();
        centerPanel.add(resultsScrollPane);
        SpringUtilities.makeCompactGrid(centerPanel, 1, 1, 12, 0, 12, 0);

        resultsTable = new JTable();
        resultsTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.setShowGrid(false);

        resultsScrollPane.getViewport().add(resultsTable);

        progressLabel = new JLabel();
        progressLabel.setForeground(Color.BLACK);
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);

        optimizeButton = new JButton("Optimize");
        optimizeButton.setMnemonic('O');

        optimizationMapButton = new JButton("Optimization Map");
        optimizationMapButton.setMnemonic('M');


        cancelButton = new JButton("Cancel");
        cancelButton.setMnemonic('C');
        cancelButton.setEnabled(false);

        closeButton = new JButton("Close");
        closeButton.setMnemonic('S');

        FlowLayout flowLayout = new FlowLayout(FlowLayout.CENTER, 5, 12);
        JPanel buttonsPanel = new JPanel(flowLayout);
        buttonsPanel.add(optimizeButton);
        buttonsPanel.add(optimizationMapButton);
        buttonsPanel.add(cancelButton);
        buttonsPanel.add(closeButton);

        progressPanel = new JPanel(new SpringLayout());
        progressPanel.add(progressBar);
        progressPanel.add(new JLabel(" Estimated remaining time: "));
        progressPanel.add(progressLabel);
        progressPanel.setVisible(false);
        SpringUtilities.makeCompactGrid(progressPanel, 1, 3, 12, 12, 12, 0);

        southPanel.add(progressPanel, BorderLayout.NORTH);
        southPanel.add(buttonsPanel, BorderLayout.SOUTH);

        getContentPane().add(northPanel, BorderLayout.NORTH);
        getContentPane().add(centerPanel, BorderLayout.CENTER);
        getContentPane().add(southPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(optimizeButton);
        setMinimumSize(MIN_SIZE);
        setPreferredSize(getMinimumSize());
    }

    private void initParams() {
        try {
            strategy = ClassFinder.getInstance(strategyName);
            paramTableModel.setParams(strategy.getParams());
            setParamTableColumns();
            resultsTableModel = new ResultsTableModel(strategy);
            resultsTable.setModel(resultsTableModel);
            DefaultTableCellRenderer renderer = (DefaultTableCellRenderer) resultsTable.getDefaultRenderer(String.class);
            renderer.setHorizontalAlignment(JLabel.RIGHT);
        } catch (Exception e) {
            MessageDialog.showError(this, e);
        }
    }

    public void setResults(List<OptimizationResult> optimizationResults) {
        this.optimizationResults = optimizationResults;
        resultsTableModel.setResults(optimizationResults);
    }

    public String getFileName() {
        return fileNameText.getText();
    }

    public int getMinTrades() {
        return Integer.parseInt(minTradesText.getText());
    }

    public PerformanceMetric getSortCriteria() {
        String selectedItem = (String) selectionCriteriaCombo.getSelectedItem();
        return PerformanceMetric.getColumn(selectedItem);
    }
    
	public MarketSnapshotFilter getDateFilter() {
		MarketSnapshotFilter filter = null;
		
		if (!ignorePeriod.isSelected()) {
			filter = MarkSnapshotUtilities.getMarketDepthFilter(sdf, fromText.getText(), toText.getText());
		}

		return filter;
    }
}
