package com.asanasoft.common.tools;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * This program contains utility methods.
 *
 */
public class Utilities {
    /*
     * This method returns the supplied YYYY-MM-DD date (or timestamp) plus some number of days.
     * If supplied number of days is negative, the effect is to subtract days.
     */
    public static String addDaysToDate(String dt, int daysToAdd) {
        String resultDate = null;

        int year = new Integer(dt.substring(0,4));
        int month = new Integer(dt.substring(5,7));
        int day = new Integer(dt.substring(8,10));
        GregorianCalendar cal = new GregorianCalendar(year, month - 1, day);    // month param is 0-based
        cal.add(Calendar.HOUR, daysToAdd * 24);
        year = cal.get(Calendar.YEAR);
        month = cal.get(Calendar.MONTH) + 1;            // month value is 0-based, so convert to 1-12 value
        day = cal.get(Calendar.DAY_OF_MONTH);
        resultDate = formatYYYY_MM_DD(year, month, day);
        if (dt.length() > 10) {
            // Append time portion of supplied timestamp.
            resultDate += dt.substring(10);
        }

        return resultDate;
    }

    /*
     * This method returns the last day of the month for the supplied year and month,
     * plus or minus some number of months.
     */
    public static String calcMonthEndDate(int year, int month, int monthIncrement) {
        String resultMonthEndDate = null;

        int incrementYears = monthIncrement / 12;
        int incrementMonths = monthIncrement % 12;
        int resultYear = year + incrementYears;
        int resultMonth = month + incrementMonths;
        if (resultMonth < 1) {
            resultYear--;
            resultMonth += 12;
        } else if (resultMonth > 12) {
            resultYear++;
            resultMonth -= 12;
        }
        int resultDay = getMonthEndDay(resultYear, resultMonth);

        resultMonthEndDate = formatYYYY_MM_DD(resultYear, resultMonth, resultDay);

        return resultMonthEndDate;
    }

    /*
     * This method returns a string in format YYYY-MM-DD for the supplied year, month, and day.
     */
    public static String formatYYYY_MM_DD(int year, int month, int day) {
        String result = null;
        String monthStr = Integer.toString(month);
        if (monthStr.length() == 1) {
            monthStr = "0" + monthStr;
        }
        String dayStr = Integer.toString(day);
        if (dayStr.length() == 1) {
            dayStr = "0" + dayStr;
        }

        result = Integer.toString(year) + "-" + monthStr + "-" + dayStr;

        return result;
    }

    /*
     * This method returns the current date in format YYYY-MM-DD, e.g. 2018-06-22.
     */
    public static String getCurrentDate() {
        String currDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        return currDate;
    }

    /**
     * This method returns the current timestamp in format YYYY-MM-DD, e.g. 2017-02-28 14:53:38.094
     */
    public static  String getCurrentTimestamp() {
        String currTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());

        return currTimestamp;
    }

    /*
     * This method returns the day of the week for a given date, e.g. "Sat".
     */
    public static String getDayOfWeek(long dateInMilliseconds) {
        String dayOfWeek = new SimpleDateFormat("EEE").format(new Date(dateInMilliseconds));

        return dayOfWeek;
    }

    /*
     * This method returns the latest month-end date in format YYYY-MM-DD, e.g. 2018-05-31.
     * If current date is itself a month-end date, then current date is returned. Otherwise, the
     * most recent prior month-end date is returned.
     * This method correctly handles February in leap years.
     */
    public static String getLatestMonthEndDate() {
        String currDate = getCurrentDate();
        String latestMonthEndDate = null;
        int currYear = new Integer(currDate.substring(0,4));
        int currMonth = new Integer(currDate.substring(5,7));
        int currDay = new Integer(currDate.substring(8));
        int currMonthEndDay = getMonthEndDay(currYear, currMonth);
        if (currDay == currMonthEndDay) {
            latestMonthEndDate = currDate;
        } else {
            latestMonthEndDate = calcMonthEndDate(currYear, currMonth, -1);
        }

        return latestMonthEndDate;
    }

    /*
     * This method returns the last day of the month for the supplied year and month.
     */
    public static int getMonthEndDay(int year, int month) {
        int monthEndDay = 31;

        if (month == 02) {
            int remainder = year % 4;
            if (remainder > 0) {
                monthEndDay = 28;
            } else {
                monthEndDay = 29;
                // if year is a century (e.g. 1900) and the century portion (e.g. 19) is
                // not divisible by four, then this is not a leap year.
                if (year % 100 == 0) {
                    int centuryPortion = year / 100;
                    if (centuryPortion % 4 > 0) {
                        monthEndDay = 28;
                    }
                }
            }
        } else if (month == 4 || month == 6 ||month == 9 ||month == 11) {
            monthEndDay = 30;
        }

        return monthEndDay;
    }

    /*
     * This method returns the previous date in format YYYY-MM-DD, e.g. 2018-06-21.
     */
    public static String getPrevDate() {
        long prevDateInMilliseconds = getPrevDateInMilliseconds();
        String prevDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date(prevDateInMilliseconds));

        return prevDate;
    }

    /*
     * This method returns the previous date, expressed in milliseconds.
     */
    public static long getPrevDateInMilliseconds() {
        long currDateInMilliseconds = new Date().getTime();
        long oneDayInMilliseconds = 24 * 3600 * 1000;   // 24 hrs/day * 3600 sec/hr * 1000 ms/sec = 1 day
        long prevDateInMilliseconds = currDateInMilliseconds - oneDayInMilliseconds;

        return prevDateInMilliseconds;
    }

    /*
     * This method returns true if the U.S. Stock Market is open on the supplied date, false if not.
     * This method is imperfect - it does not attempt to identify Good Friday or any other special closed dates there may be.
     */
    public static boolean isUSStockMarketOpen(long dateInMilliseconds) {
        boolean isOpen;
        String dt = new SimpleDateFormat("yyyy-MM-dd").format(new Date(dateInMilliseconds));
        String dayOfWeek = getDayOfWeek(dateInMilliseconds);
        int month = Integer.valueOf(dt.substring(5,7));
        int day = Integer.valueOf(dt.substring(8));
        if (dayOfWeek.equals("Sat") ||
                dayOfWeek.equals("Sun") ||
                (month == 1 && day == 1) ||                                             // New Year's Day
                (month == 1 && day <= 3 && dayOfWeek.equals("Mon")) ||                  // New Year's Day (observed)
                (month == 1 && day >= 15 && day <= 21 && dayOfWeek.equals("Mon")) ||    // MLK Day
                (month == 2 && day >= 15 && day <= 21 && dayOfWeek.equals("Mon")) ||    // President's Day
                (month == 5 && day >= 25 && day <= 31 && dayOfWeek.equals("Mon")) ||    // Memorial Day
                (month == 7 && day == 4) ||                                             // July 4th
                (month == 9 && day >= 1 && day <= 7 && dayOfWeek.equals("Mon")) ||      // Labor Day
                (month == 11 && day >= 22 && day <= 28 && dayOfWeek.equals("Thu")) ||   // Thanksgiving
                (month == 12 && day == 25)                                              // Christmas
                ) {
            isOpen = false;
        } else {
            isOpen = true;
        }

        return isOpen;
    }

    /*
     * This method replaces any symbolic date literals (e.g. "PreviousDate") with actual date values (e.g. "2018-06-22").
     * Symbolic date literals supported are:
     *      CurrentDate
     *      CurrentYear
     *      PreviousDate
     *      LatestMonthEndDate
     *      LatestMonthEndDateMinus<n>Months    where <n> is an integer
     */
    public static String replaceSymbolicDatesInString(String inString) {
        String result = inString;

        if (inString != null && !inString.isEmpty())
        {
            if (result.contains("CurrentDate")) {
                String currDate = Utilities.getCurrentDate();
                result = result.replaceAll("CurrentDate", currDate);
            }

            if (result.contains("CurrentYear")) {
                String currDate = Utilities.getCurrentDate();
                result = result.replaceAll("CurrentYear", currDate.substring(0,4));
            }

            if (result.contains("PreviousDate")) {
                String prevDate = Utilities.getPrevDate();
                result = result.replaceAll("PreviousDate", prevDate);
            }

            int infiniteLoopSafetyCtr = 0;
            while (result.contains("LatestMonthEndDate") && infiniteLoopSafetyCtr < 100) {
                infiniteLoopSafetyCtr++;
                String latestMonthEndDate = Utilities.getLatestMonthEndDate();

                int posLit = result.indexOf("LatestMonthEndDate");
                int posMinusLit = posLit + "LatestMonthEndDate".length();
                int posMonthIncrement = posMinusLit + "Minus".length();

                if (posMonthIncrement < result.length() &&
                        result.substring(posMinusLit,posMonthIncrement).equals("Minus")) {
                    // Extract literalString (e.g. "LatestMonthEndDateMinus7Months").
                    int posMonthsLit = result.indexOf("Months",posMonthIncrement);
                    if (posMonthsLit > posMonthIncrement) {
                        String literalString = result.substring(posLit,posMonthsLit+6);

                        // Extract number of months from literalString (e.g. 7 from "LatestMonthEndDateMinus7Months").
                        int monthIncrement = new Integer(result.substring(posMonthIncrement,posMonthsLit));

                        // Calculate date.
                        int latestMonthEndYear = new Integer(latestMonthEndDate.substring(0,4));
                        int latestMonthEndMonth = new Integer(latestMonthEndDate.substring(5,7));
                        String latestMonthEndDateMinusMonths = Utilities.calcMonthEndDate(latestMonthEndYear, latestMonthEndMonth, -1 * monthIncrement);

                        result = result.replace(literalString, latestMonthEndDateMinusMonths);
                    }
                } else {
                    result = result.replace("LatestMonthEndDate", latestMonthEndDate);
                }
            }
        }

        return result;
    }
}
