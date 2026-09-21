package com.linkup.user.notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PushDeviceRepository extends JpaRepository<PushDevice, String> {
    List<PushDevice> findByUserId(String userId);
    void deleteByIdAndUserId(String id, String userId);
}
