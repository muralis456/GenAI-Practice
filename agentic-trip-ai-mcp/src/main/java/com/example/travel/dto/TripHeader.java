package com.example.travel.dto;

import java.io.Serial;
import java.io.Serializable;

public class TripHeader implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String title = "";
    private String origin = "";
    private String destination = "";
    private String originIata = "";
    private String destinationIata = "";
    private String departureDate = "";
    private String returnDate = "";
    private int nights;
    private int travelers = 1;
    private String travelStyle = "";
    private String budgetLabel = "";
    private String audienceLabel = "";
    private String status = "";
    private boolean awaitingApproval;
    private int qualityScore;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin == null ? "" : origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination == null ? "" : destination;
    }

    public String getOriginIata() {
        return originIata;
    }

    public void setOriginIata(String originIata) {
        this.originIata = originIata == null ? "" : originIata;
    }

    public String getDestinationIata() {
        return destinationIata;
    }

    public void setDestinationIata(String destinationIata) {
        this.destinationIata = destinationIata == null ? "" : destinationIata;
    }

    public String getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(String departureDate) {
        this.departureDate = departureDate == null ? "" : departureDate;
    }

    public String getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(String returnDate) {
        this.returnDate = returnDate == null ? "" : returnDate;
    }

    public int getNights() {
        return nights;
    }

    public void setNights(int nights) {
        this.nights = nights;
    }

    public int getTravelers() {
        return travelers;
    }

    public void setTravelers(int travelers) {
        this.travelers = travelers;
    }

    public String getTravelStyle() {
        return travelStyle;
    }

    public void setTravelStyle(String travelStyle) {
        this.travelStyle = travelStyle == null ? "" : travelStyle;
    }

    public String getBudgetLabel() {
        return budgetLabel;
    }

    public void setBudgetLabel(String budgetLabel) {
        this.budgetLabel = budgetLabel == null ? "" : budgetLabel;
    }

    public String getAudienceLabel() {
        return audienceLabel;
    }

    public void setAudienceLabel(String audienceLabel) {
        this.audienceLabel = audienceLabel == null ? "" : audienceLabel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null ? "" : status;
    }

    public boolean isAwaitingApproval() {
        return awaitingApproval;
    }

    public void setAwaitingApproval(boolean awaitingApproval) {
        this.awaitingApproval = awaitingApproval;
    }

    public int getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(int qualityScore) {
        this.qualityScore = qualityScore;
    }
}
