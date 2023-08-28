package com.lucario.antidhrishti;

import android.graphics.Bitmap;
import android.graphics.RectF;

import java.io.Serializable;

public class SerializableRecognition implements Serializable {
    private String id;
    private String title;
    private Float distance;
    private Object extra;
    transient private RectF location;
    private Integer color;
    transient private Bitmap crop;

    private float left;
    private float top;
    private float right;
    private float bottom;

    public SerializableRecognition(SimilarityClassifier.Recognition rec){
        this.id = rec.getId();
        this.title = rec.getTitle();
        this.distance = rec.getDistance();
        this.location = rec.getLocation();
        this.color = rec.getColor();
        this.extra = rec.getExtra();
        this.crop = rec.getCrop();
        this.left = location.left;
        this.right = location.right;
        this.top = location.top;
        this.bottom = location.bottom;
    }
    public SimilarityClassifier.Recognition getRec(){
        SimilarityClassifier.Recognition rec = new SimilarityClassifier.Recognition(id, title, distance, new RectF(left, top, right, bottom));
        rec.setExtra(extra);
        rec.setColor(color);
        rec.setCrop(crop);
        return rec;
    }
}
