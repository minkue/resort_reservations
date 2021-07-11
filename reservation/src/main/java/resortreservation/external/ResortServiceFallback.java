package resortreservation.external;

import org.springframework.stereotype.Component;


@Component
public class ResortServiceFallback implements ResortService {

    @Override
    public Resort getResortStatus(Long id){
        System.out.println("Circuit breaker has been opened. Fallback returned instead.");
        return null;
    }

}