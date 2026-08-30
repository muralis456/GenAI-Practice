package com.example.travel.model;

import java.io.Serial;
import java.io.Serializable;

public class HotelOption implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private String area;
    private String priceRange;
    private String rating;
    private String suitableFor;
    private String notes;

    public HotelOption() {
    }

    public HotelOption(String name, String area, String priceRange, String notes) {
        this.name = name;
        this.area = area;
        this.priceRange = priceRange;
        this.notes = notes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getPriceRange() {
        return priceRange;
    }

    public void setPriceRange(String priceRange) {
        this.priceRange = priceRange;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public String getSuitableFor() {
        return suitableFor;
    }

    public void setSuitableFor(String suitableFor) {
        this.suitableFor = suitableFor;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String toDisplay() {
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isBlank()) {
            sb.append(name);
        }
        if (area != null && !area.isBlank() && !isPlaceholder(area)) {
            sb.append(" (").append(area).append(')');
        }
        if (priceRange != null && !priceRange.isBlank() && !isPlaceholder(priceRange)) {
            sb.append(" — ").append(priceRange);
        }
        if (rating != null && !rating.isBlank() && !isPlaceholder(rating)) {
            sb.append(" · ").append(rating);
        }
        if (notes != null && !notes.isBlank() && !isPlaceholder(notes)) {
            sb.append(". ").append(notes);
        }
        return sb.toString();
    }

    private static boolean isPlaceholder(String value) {
        String normalized = value.trim().toLowerCase();
        return normalized.equals("not specified") || normalized.equals("n/a") || normalized.equals("unknown");
    }
}
