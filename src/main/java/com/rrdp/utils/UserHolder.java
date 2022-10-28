package com.rrdp.utils;

import com.rrdp.dto.UserDTO;
import com.rrdp.entity.User;

/**
 * ThreadLocal 线程变量，每个线程拥有一个User的拷贝
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
