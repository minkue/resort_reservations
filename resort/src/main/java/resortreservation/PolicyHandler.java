package resortreservation;

import resortreservation.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @Autowired ResortRepository resortRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverReservationCanceled_ResortStatusChangePolicy(@Payload ReservationCanceled reservationCanceled){

        if(!reservationCanceled.validate()) return;

        System.out.println("\n\n##### listener ResortStatusChangePolicy : " + reservationCanceled.toJson() + "\n\n");

        // 리조트 상태를 예약 가능 상태로 변경
         resortRepository.findById(reservationCanceled.getResortId())
            .ifPresent(
                resort -> {
                    resort.setResortStatus("Available");
                    resortRepository.save(resort);
            }
        )
        ; 
            
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverReservationRegistered_ResortStatusChangePolicy(@Payload ReservationRegistered reservationRegistered){

        if(!reservationRegistered.validate()) return;

        System.out.println("\n\n##### listener ResortStatusChangePolicy : " + reservationRegistered.toJson() + "\n\n");

        // 리조트 상태를 예약 불가능 상태로 변경
        resortRepository.findById(reservationRegistered.getResortId())
            .ifPresent(
                resort -> {
                    resort.setResortStatus("Not Available");
                    resortRepository.save(resort);
            }
        )
        ;    
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}


}
