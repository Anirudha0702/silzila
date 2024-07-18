package com.silzila.querybuilder;

import com.silzila.exception.BadRequestException;
import com.silzila.helper.AilasMaker;
import com.silzila.payload.internals.QueryClauseFieldListMap;
import com.silzila.payload.request.Dimension;
import com.silzila.payload.request.Measure;
import com.silzila.payload.request.Query;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class SelectClauseTeraData {

    private static final Logger logger = LogManager.getLogger(SelectClauseSqlserver.class);

    /* SELECT clause for SqlServer dialect */
    public static QueryClauseFieldListMap buildSelectClause(Query req, String vendorName, Map<String,Integer>... aliasnumber) throws BadRequestException {
        logger.info("SelectClauseTeraData calling ***********");

        Map<String, Integer> aliasNumbering = new HashMap<>();
        // aliasing for only measure  override
        Map<String,Integer> aliasNumberingM = new HashMap<>();

        if (aliasnumber != null && aliasnumber.length > 0) {
            Map<String, Integer> aliasNumber = aliasnumber[0];
            aliasNumber.forEach((key, value) -> aliasNumberingM.put(key, value));
        }

        List<String> selectList = new ArrayList<>();
        List<String> selectDimList = new ArrayList<>();
        List<String> selectMeasureList = new ArrayList<>();
        List<String> groupByDimList = new ArrayList<>();
        List<String> orderByDimList = new ArrayList<>();


        Map<String, String> timeGrainMap = Map.of("YEAR", "YEAR", "MONTH", "MONTH", "QUARTER", "QUARTER",
                "DAYOFWEEK", "WEEKDAY", "DAYOFMONTH", "DAY");

        /*
         * --------------------------------------------------------
         * ---------------- Iterate List of Dim Fields ------------
         * --------------------------------------------------------
         * Dim fields are added in Group by and Order by clause. Some Dims (like month)
         * require extra index for sorting.
         *
         * So, Group by and Order by clause are added at column level and Select clause
         * is added at the end of Dim/Measure
         */
        for (int i = 0; i < req.getDimensions().size(); i++) {
            Dimension dim = req.getDimensions().get(i);
            // If the base dimension goes up to order_date_2 and the measure is order_date, it should be order_date_3.
            // If the overridden dimension includes additional order_date values, we want to keep the measure as order_date_3.
            if(aliasnumber != null && aliasnumber.length > 0){

                for(String key : aliasNumberingM.keySet()){

                    for(String key1 : aliasNumbering.keySet()){
                        if(key.equals(req.getMeasures().get(0).getFieldName()) && key.equals(key1) && aliasNumbering.get(key).equals(aliasNumberingM.get(key1))){
                            aliasNumbering.put(key, aliasNumbering.get(key) + 1);
                        }
                    }
                }

            }
            String field = "";

            // for non Date fields, Keep column as is
            if (List.of("TEXT", "BOOLEAN", "INTEGER", "DECIMAL").contains(dim.getDataType().name())) {
                field = dim.getTableId() + "." + dim.getFieldName();
                groupByDimList.add(field);
                orderByDimList.add(field);
            }
            // for date fields, need to Parse as year, month, etc.. to aggreate
            else if (List.of("DATE", "TIMESTAMP").contains(dim.getDataType().name())) {

                // checking ('year', 'quarter', 'month', 'yearmonth', 'yearquarter',
                // 'dayofweek', 'date', 'dayofmonth')
                // year -> 2015
                if (dim.getTimeGrain().name().equals("YEAR")) {
                    field = "YEAR(" + dim.getTableId() + "." + dim.getFieldName() + ")";
                    groupByDimList.add(field);
                    orderByDimList.add(field);
                }
                // quarter name -> Q3
                else if (dim.getTimeGrain().name().equals("QUARTER")) {
                    field = "CONCAT('Q', LTRIM(TD_QUARTER_OF_YEAR( " + dim.getTableId() + "." + dim.getFieldName() + ")))";
                    groupByDimList.add(field);
                    orderByDimList.add(field);
                }
                // month name -> August
                // for month, need to give month number also for column sorting
                // which should be available in group by list but not in select list
                else if (dim.getTimeGrain().name().equals("MONTH")) {
                    String sortingField = "MONTH(" + dim.getTableId() + "." + dim.getFieldName() + ")";
                    field = "case " +sortingField+"\n"+
                            "    when '01' then 'January'\n" +
                            "    when '02' then 'February'\n" +
                            "    when '03' then 'March'\n" +
                            "    when '04' then 'April'\n" +
                            "    when '05' then 'May'\n" +
                            "    when '06' then 'June'\n" +
                            "    when '07' then 'July'\n" +
                            "    when '08' then 'August'\n" +
                            "    when '09' then 'September'\n" +
                            "    when '10' then 'October'\n" +
                            "    when '11' then 'November'\n" +
                            "    when '12' then 'December'\n" +
                            "    else ''\n" +
                            "    end";
                    groupByDimList.add(sortingField);
                    groupByDimList.add(field);
                    orderByDimList.add(sortingField);
                }
                // yearquarter name -> 2015-Q3
                else if (dim.getTimeGrain().name().equals("YEARQUARTER")) {
                    field = "CONCAT(LTRIM(YEAR(" + dim.getTableId() + "." + dim.getFieldName()
                            + ")), '-Q', LTRIM(TD_QUARTER_OF_YEAR( " + dim.getTableId() + "." + dim.getFieldName() + ")))";
                    groupByDimList.add(field);
                    orderByDimList.add(field);
                }
                // yearmonth name -> 2015-08
                else if (dim.getTimeGrain().name().equals("YEARMONTH")) {
                    field = "CONCAT(LTRIM(YEAR("+ dim.getTableId() + "." + dim.getFieldName() +")),'-',LTRIM(MONTH("+ dim.getTableId() + "." + dim.getFieldName() +")(format '99')))";
                    groupByDimList.add(field);
                    orderByDimList.add(field);
                }
                // date -> 2022-08-31
                else if (dim.getTimeGrain().name().equals("DATE")) {
                    field = "(" + dim.getTableId() + "." + dim.getFieldName() + ")";
                    groupByDimList.add(field);
                    orderByDimList.add(field);
                }
                // day Name -> Wednesday
                // for day of week, also give day of week number for column sorting
                // which should be available in group by list but not in select list
                else if (dim.getTimeGrain().name().equals("DAYOFWEEK")) {
                    String sortingField = "TD_DAY_OF_WEEK(" + dim.getTableId() + "." + dim.getFieldName() + ")";
                    field = " case  "+sortingField+"\n" +
                            "\t\twhen 1 then 'Sunday'\n" +
                            "\t\twhen 2 then 'Monday'\n" +
                            "\t\twhen 3 then 'Tuesday'\n" +
                            "\t\twhen 4 then 'Wednesday'\n" +
                            "\t\twhen 5 then 'Thursday'\n" +
                            "\t\twhen 6 then 'Friday'\n" +
                            "\t\twhen 7 then 'Saturday'\n" +
                            "\t\tend";
                    groupByDimList.add(sortingField);
                    groupByDimList.add(field);
                    orderByDimList.add(sortingField);
                }
                // day of month -> 31
                else if (dim.getTimeGrain().name().equals("DAYOFMONTH")) {
                    field = "MONTH(" + dim.getTableId() + "." + dim.getFieldName() + ")";
                    groupByDimList.add(field);
                    orderByDimList.add(field);
                } else {
                    throw new BadRequestException("Error: Dimension " + dim.getFieldName() +
                            " should have timegrain!");
                }
            }
            String alias = AilasMaker.aliasing(dim.getFieldName(), aliasNumbering);
            selectDimList.add(field + " AS " + alias);
        }
        ;

        /*
         * --------------------------------------------------------
         * ------------- Iterate List of Measure Fields -----------
         * --------------------------------------------------------
         */
        for (int i = 0; i < req.getMeasures().size(); i++) {
            Measure meas = req.getMeasures().get(i);

            // if aggr is null then throw error
            // if (meas.getAggr() == null || meas.getAggr().isBlank()) {
            // throw new BadRequestException(
            // "Error: Aggregation is not specified for measure " + meas.getFieldName());
            // }

            // if text field in measure then use
            // Text Aggregation Methods like COUNT
            // checking ('count', 'countnn', 'countn', 'countu')
            String field = "";
            String windowFn = "";
            if (List.of("TEXT", "BOOLEAN").contains(meas.getDataType().name())) {
                // checking ('count', 'countnn', 'countn', 'countu')
                if (meas.getAggr().name().equals("COUNT")) {
                    field = "COUNT(*)";
                } else if (meas.getAggr().name().equals("COUNTNN")) {
                    field = "COUNT(" + meas.getTableId() + "." + meas.getFieldName() + ")";
                } else if (meas.getAggr().name().equals("COUNTU")) {
                    field = "COUNT(DISTINCT " + meas.getTableId() + "." + meas.getFieldName() + ")";
                } else if (meas.getAggr().name().equals("COUNTN")) {
                    field = "SUM(CASE WHEN " + meas.getTableId() + "." + meas.getFieldName()
                            + " IS NULL THEN 1 ELSE 0 END)";
                } else {
                    throw new BadRequestException(
                            "Error: Aggregation is not correct for measure " + meas.getFieldName());
                }
            }

            // for date fields, parse to year, month, etc.. and then
            // aggregate the field for Min & Max only
            else if (List.of("DATE", "TIMESTAMP").contains(meas.getDataType().name())) {

                // if date fields don't have time grain, then throw error
                if (Objects.isNull(meas.getTimeGrain())) {
                    throw new BadRequestException(
                            "Error: Date/Timestamp measure should have timeGrain");
                }

                List<String> aggrList = List.of("MIN", "MAX");
                List<String> timeGrainList = List.of("YEAR", "QUARTER", "MONTH", "DATE", "DAYOFMONTH", "DAYOFWEEK");
                // checking Aggregations: ('min', 'max', 'count', 'countnn', 'countn', 'countu')
                // checking Time Grains: ('year', 'quarter', 'month', 'yearmonth',
                // 'yearquarter', 'dayofmonth')

                if (aggrList.contains(meas.getAggr().name()) && timeGrainList.contains(meas.getTimeGrain().name())) {

                    //checking for ('date','quarter,year, month,dayofmonth and dayofweek)
                    if (meas.getTimeGrain().name().equals("DATE")) {
                        field = meas.getAggr().name() + " (CAST( " + meas.getTableId() + "."
                                + meas.getFieldName() + ") AS DATE)";
                    } else if((meas.getTimeGrain().name().equals("QUARTER"))){
                        field = meas.getAggr().name() + " CAST(TD_QUARTER_OF_YEAR(" + meas.getTableId()
                                + "." + meas.getFieldName() + ") AS INT)";
                    }else if((meas.getTimeGrain().name().equals("YEAR"))){
                        field = meas.getAggr().name() + " CAST(Extract(" + timeGrainMap.get(meas.getTimeGrain().name())
                                + " FROM " + meas.getTableId()
                                + "." + meas.getFieldName() + ") AS INT)";
                    }else if((meas.getTimeGrain().name().equals("MONTH"))){
                        field = meas.getAggr().name() + " CAST(Extract(" + timeGrainMap.get(meas.getTimeGrain().name())
                                + " FROM " + meas.getTableId()
                                + "." + meas.getFieldName() + ") AS INT)";
                    }
                    else if((meas.getTimeGrain().name().equals("DAYOFMONTH"))){
                        field = meas.getAggr().name() + "CAST(TD_DAY_OF_MONTH (" + meas.getTableId()
                                + "." + meas.getFieldName() + ") AS INT)";
                    }else if((meas.getTimeGrain().name().equals("DAYOFWEEK"))){
                        field = meas.getAggr().name() + "CAST(TD_DAY_OF_WEEK (" + meas.getTableId()
                                + "." + meas.getFieldName() + ") AS INT)";
                    }
                }

                /*
                 * countu is a special case & we can use time grain for this measure
                 */
                // checking ('year', 'month')
                else if (meas.getAggr().name().equals("COUNTU")
                        && List.of("YEAR", "MONTH")
                        .contains(meas.getTimeGrain().name())) {
                    field = "COUNT(DISTINCT EXTRACT(" + timeGrainMap.get(meas.getTimeGrain().name())
                            + " FROM " + meas.getTableId() + "." + meas.getFieldName() + "))";
                }
                // checking ('date')
                else if (meas.getAggr().name().equals("COUNTU") && meas.getTimeGrain().name().equals("DATE")) {
                    field = "COUNT(DISTINCT CAST( " + meas.getTableId() + "." + meas.getFieldName() + " AS DATE))";
                }
                // checking ('yearquarter')
                else if (meas.getAggr().name().equals("COUNTU") && meas.getTimeGrain().name().equals("YEARQUARTER")) {
                    field = "COUNT(DISTINCT(CONCAT(LTRIM(YEAR(" + meas.getTableId() + "." + meas.getFieldName()
                            + ")), '-Q', LTRIM(TD_QUARTER_OF_YEAR( " + meas.getTableId() + "." + meas.getFieldName() + "))))";
                }
                // checking ('yearmonth')
                else if (meas.getAggr().name().equals("COUNTU") && meas.getTimeGrain().name().equals("YEARMONTH")) {
                    field = "COUNT(DISTINCT(CONCAT(LTRIM(YEAR("+ meas.getTableId() + "." + meas.getFieldName() +")),'-',LTRIM(MONTH("+ meas.getTableId() + "." + meas.getFieldName() +")(format '99'))))";
                }
                // checking ('quarter')
                else if(meas.getAggr().name().equals("COUNTU") && meas.getTimeGrain().name().equals("QUARTER")){
                    field= "COUNT(DISTINCT CAST(TD_QUARTER_OF_YEAR(" + meas.getTableId()
                            + "." + meas.getFieldName() + ") AS INT)";
                }
                // checking ('dayofweek')
                else if(meas.getAggr().name().equals("COUNTU") && meas.getTimeGrain().name().equals("DAYOFWEEK")){
                    field= "COUNT(DISTINCT CAST(TD_DAY_OF_WEEK (" + meas.getTableId()
                            + "." + meas.getFieldName() + ") AS INT)";
                }
                // checking ('dayofmonth')
                else if(meas.getAggr().name().equals("COUNTU") && meas.getTimeGrain().name().equals("DAYOFMONTH")){
                    field= "COUNT(DISTINCT CAST(TD_DAY_OF_MONTH (" + meas.getTableId()
                            + "." + meas.getFieldName() + ") AS INT)";
                }

                /*
                 * for simple count & variants, time grain is not needed
                 */
                else if (meas.getAggr().name().equals("COUNT")) {
                    field = "COUNT(*)";
                } else if (meas.getAggr().name().equals("COUNTNN")) {
                    field = "COUNT(" + meas.getTableId() + "." + meas.getFieldName() + ")";
                } else if (meas.getAggr().name().equals("COUNTN")) {
                    field = "SUM(CASE WHEN " + meas.getTableId() + "." + meas.getFieldName()
                            + " IS NULL THEN 1 ELSE 0 END)";
                } else {
                    throw new BadRequestException("Error: Measure " + meas.getFieldName() +
                            " should have timegrain!");
                }
            }

            // for number fields, do aggregation
            else if (List.of("INTEGER", "DECIMAL").contains(meas.getDataType().name())) {
                if (List.of("SUM", "AVG", "MIN", "MAX").contains(meas.getAggr().name())) {
                    field = meas.getAggr().name() + "(" + meas.getTableId() + "." + meas.getFieldName()
                            + ")";
                } else if (meas.getAggr().name().equals("COUNT")) {
                    field = "COUNT(*)";
                } else if (meas.getAggr().name().equals("COUNTNN")) {
                    field = "COUNT(" + meas.getTableId() + "." + meas.getFieldName() + ")";
                } else if (meas.getAggr().name().equals("COUNTU")) {
                    field = "COUNT(DISTINCT " + meas.getTableId() + "." + meas.getFieldName() + ")";
                } else if (meas.getAggr().name().equals("COUNTN")) {
                    field = "SUM(CASE WHEN " + meas.getTableId() + "." + meas.getFieldName()
                            + " IS NULL THEN 1 ELSE 0 END)";
                } else {
                    throw new BadRequestException(
                            "Error: Aggregation is not correct for Numeric field " + meas.getFieldName());
                }
            }
            // if windowFn not null it will execute window function for sqlserver
            if(meas.getWindowFn()[0] != null){
                windowFn = SelectClauseWindowFunction.windowFunction(meas, req, field, vendorName);
                String alias = AilasMaker.aliasing(meas.getFieldName(), aliasNumbering);
                // if aliasnumber is not null, to maintain alias sequence for measure field
                if(aliasnumber != null && aliasnumber.length > 0){
                    alias= AilasMaker.aliasing(meas.getFieldName(), aliasNumberingM);
                }
                // selectMeasureList.add(field + " AS " + alias);
                selectMeasureList.add(windowFn + " AS " + alias);
            } else{
                String alias = AilasMaker.aliasing(meas.getFieldName(), aliasNumbering);
                // if aliasnumber is not null, to maintain alias sequence for measure field
                if(aliasnumber != null && aliasnumber.length > 0){
                    alias= AilasMaker.aliasing(meas.getFieldName(), aliasNumberingM);
                }
                selectMeasureList.add(field + " AS " + alias);
            }
        }
        ;

        selectList.addAll(selectDimList);
        selectList.addAll(selectMeasureList);
        QueryClauseFieldListMap qFieldListMap = new QueryClauseFieldListMap(selectList, groupByDimList,
                orderByDimList);
        return qFieldListMap;
    }
}