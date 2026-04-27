package org.example.lottery_system.service.impl;

import org.example.lottery_system.common.errorcode.ServiceErrorCodeConstants;
import org.example.lottery_system.common.exception.ServiceException;
import org.example.lottery_system.service.PictureService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.UUID;

@Service
public class PictureServiceImpl implements PictureService {
    @Value("${pic.local-path}")
    private String localPath;
    @Override
    public String savePicture(MultipartFile multipartFile) {
        //创建目录
        File dir=new File(localPath);
        if(!dir.exists()){
            dir.mkdirs();
        }
        //创建索引
        String fileName=multipartFile.getOriginalFilename();
        assert fileName!=null;
        String suffix=fileName.substring(fileName.lastIndexOf("."));
        fileName= UUID.randomUUID().toString()+suffix;
        //图片保存
        try {
            multipartFile.transferTo(new File(localPath,fileName));
        } catch (Exception e) {
           throw new ServiceException(ServiceErrorCodeConstants.PIC_ERROR);
        }
        return fileName;
    }
}
