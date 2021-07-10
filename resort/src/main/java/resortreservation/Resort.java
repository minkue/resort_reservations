package resortreservation;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;
import java.util.Date;

@Entity
@Table(name="Resort_table")
public class Resort {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String resortName;
    private String resortStatus;
    private String resortType;
    private String resortPeriod;
    private Float resortPrice;

    @PostPersist
    public void onPostPersist(){
        ResortRegistrated resortRegistrated = new ResortRegistrated();
        BeanUtils.copyProperties(this, resortRegistrated);
        resortRegistrated.publishAfterCommit();


    }

    @PostUpdate
    public void onPostUpdate(){
        ResortStatusChanged resortStatusChanged = new ResortStatusChanged();
        BeanUtils.copyProperties(this, resortStatusChanged);
        resortStatusChanged.publishAfterCommit();


    }

    @PostRemove
    public void onPostRemove(){
        ResortRemoved resortRemoved = new ResortRemoved();
        BeanUtils.copyProperties(this, resortRemoved);
        resortRemoved.publishAfterCommit();


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
