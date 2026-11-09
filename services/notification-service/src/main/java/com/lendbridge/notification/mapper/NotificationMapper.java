package com.lendbridge.notification.mapper;

import com.lendbridge.notification.dto.NotificationResponse;
import com.lendbridge.notification.model.Notification;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

  NotificationResponse toResponse(Notification notification);

  List<NotificationResponse> toResponseList(List<Notification> notifications);
}
