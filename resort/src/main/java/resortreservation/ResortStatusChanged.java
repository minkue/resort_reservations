package resortreservation;

public class ResortStatusChanged extends AbstractEvent {

    private Long id;
    private String resortName;
    private String resortStatus;
    private String resortType;
    private String resortPeriod;
    private Float resortPrice;

    public ResortStatusChanged(){
        super();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getResortName() {
        return resortName;
    }

    public void setResortName(String resortName) {
        this.resortName = resortName;
    }
    public String getResortStatus() {
        return resortStatus;
    }

    public void setResortStatus(String resortStatus) {
        this.resortStatus = resortStatus;
    }
    public String getResortType() {
        return resortType;
    }

    public void setResortType(String resortType) {
        this.resortType = resortType;
    }
    public String getResortPeriod() {
        return resortPeriod;
    }

    public void setResortPeriod(String resortPeriod) {
        this.resortPeriod = resortPeriod;
    }
    public Float getResortPrice() {
        return resortPrice;
    }

    public void setResortPrice(Float resortPrice) {
        this.resortPrice = resortPrice;
    }
}
