package com.lucario.antidhrishti;

import java.util.Date;

public class ClassDataModel {
    private String className;
    private Date startTime;
    private Date endTime;
    private int timeCredits;

    private boolean attended;
    private boolean canAttend;

    public ClassDataModel(String className, Date startTime, Date endTime, int timeCredits, boolean attended, boolean canAttend) {
        this.className = className;
        this.startTime = startTime;
        this.endTime = endTime;
        this.timeCredits = timeCredits;
        this.attended = attended;
        this.canAttend = canAttend;
    }

    public String getClassName() {
        return className;
    }

    public Date getStartTime() {
        return startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public int getTimeCredits() {
        return timeCredits;
    }

    public boolean isAttended() {
        return attended;
    }

    public boolean canAttend() {
        return canAttend;
    }

}
