package org.example.lottery_system.service;

import org.springframework.web.multipart.MultipartFile;

public interface PictureService {
    // 保存图片
    String savePicture(MultipartFile file);
}
