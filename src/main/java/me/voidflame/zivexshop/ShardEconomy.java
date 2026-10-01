package me.voidflame.zivexshop;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;

final class ShardEconomy {
    private static final String SERVICE_NAME = "me.voidflame.zivexshards.ShardService";

    private Object service;
    private Method balance;
    private Method withdraw;
    private Method deposit;

    private boolean resolve() {
        try {
            service = null;
            balance = null;
            withdraw = null;
            deposit = null;

            for (Class<?> serviceType : Bukkit.getServicesManager().getKnownServices()) {
                if (!SERVICE_NAME.equals(serviceType.getName())) continue;

                @SuppressWarnings({"rawtypes", "unchecked"})
                RegisteredServiceProvider<?> registration =
                        Bukkit.getServicesManager().getRegistration((Class) serviceType);

                if (registration == null || registration.getProvider() == null) return false;

                Object provider = registration.getProvider();
                Method getBalance = serviceType.getMethod("getBalance", UUID.class);
                Method take = serviceType.getMethod("withdraw", UUID.class, long.class);
                Method add = serviceType.getMethod("deposit", UUID.class, long.class);

                service = provider;
                balance = getBalance;
                withdraw = take;
                deposit = add;
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            service = null;
            balance = null;
            withdraw = null;
            deposit = null;
        }
        return false;
    }

    long balance(UUID id) {
        try {
            if (service == null && !resolve()) return -1;
            Object value = balance.invoke(service, id);
            return value instanceof Number number ? number.longValue() : -1;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            service = null;
            return -1;
        }
    }

    boolean withdraw(UUID id, long amount) {
        try {
            if (service == null && !resolve()) return false;
            return Boolean.TRUE.equals(withdraw.invoke(service, id, amount));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            service = null;
            return false;
        }
    }

    boolean deposit(UUID id, long amount) {
        try {
            if (service == null && !resolve()) return false;
            return Boolean.TRUE.equals(deposit.invoke(service, id, amount));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            service = null;
            return false;
        }
    }
}
