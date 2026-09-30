package me.voidflame.zivexshop;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.lang.reflect.Method;
import java.util.UUID;

final class ShardEconomy {
    private Object service;
    private Method balance, withdraw, deposit;

    private Class<?> api(){try{return Class.forName("me.voidflame.zivexshards.ShardService");}catch(ClassNotFoundException e){return null;}}
    private boolean resolve(){
        try{
            Class<?> type=api(); if(type==null)return false;
            RegisteredServiceProvider<?> reg=Bukkit.getServicesManager().getRegistration(type);
            if(reg==null){service=null;return false;}
            service=reg.getProvider();
            balance=type.getMethod("getBalance",UUID.class);
            withdraw=type.getMethod("withdraw",UUID.class,long.class);
            deposit=type.getMethod("deposit",UUID.class,long.class);
            return service!=null;
        }catch(Exception e){service=null;return false;}
    }
    long balance(UUID id){try{if(service==null&&!resolve())return -1;Object v=balance.invoke(service,id);return v instanceof Number n?n.longValue():-1;}catch(Exception e){service=null;return -1;}}
    boolean withdraw(UUID id,long amount){try{if(service==null&&!resolve())return false;return Boolean.TRUE.equals(withdraw.invoke(service,id,amount));}catch(Exception e){service=null;return false;}}
    boolean deposit(UUID id,long amount){try{if(service==null&&!resolve())return false;return Boolean.TRUE.equals(deposit.invoke(service,id,amount));}catch(Exception e){service=null;return false;}}
}
