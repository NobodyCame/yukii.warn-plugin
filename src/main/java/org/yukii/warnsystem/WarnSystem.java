package org.yukii.warnsystem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


//Почти всё здесь написано ии или дописано автодополнением IDE, что не сделано ИИ слеплено из стаковерфлоу и тому подобного, я никогда раньше не учил джаву, знаю только принцып работы языков програмированния
//JetBrains onelove <3
public final class WarnSystem extends JavaPlugin implements CommandExecutor {

    private final ConcurrentHashMap<UUID, Integer> warns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastWarnTimestamp = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        Objects.requireNonNull(getCommand("пред")).setExecutor(this);
        Objects.requireNonNull(getCommand("преды")).setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NonNull [] args) {

        // Команда преды
        if (label.equalsIgnoreCase("преды")) {
            if (args.length == 0) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Только игроки могут смотреть свои предупреждения!");
                    return true;
                }
                int count = warns.getOrDefault(player.getUniqueId(), 0);
                player.sendMessage(Component.text("Ваши предупреждения: ", NamedTextColor.GRAY)
                        .append(Component.text(count, NamedTextColor.RED)));
                return true;
            }

            if (!sender.hasPermission("warnsystem.use")) {
                sender.sendMessage(Component.text("У вас нет прав на просмотр чужих предупреждений!", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
                return true;
            }
            int count = warns.getOrDefault(target.getUniqueId(), 0);
            sender.sendMessage(Component.text("Предупреждения " + target.getName() + ": ", NamedTextColor.GRAY)
                    .append(Component.text(count, NamedTextColor.RED)));
            return true;
        }

        // Команда пред
        if (label.equalsIgnoreCase("пред")) {
            if (!sender.hasPermission("warnsystem.use")) {
                sender.sendMessage(Component.text("У вас нет права на выдачу предупреждений!", NamedTextColor.RED));
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(Component.text("Использование: /пред [ник] (причина)", NamedTextColor.RED));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Игрок не в сети.", NamedTextColor.RED));
                return true;
            }

            long now = System.currentTimeMillis();
            long cooldown = 30000; // 60000 = 1 минута (колДАУН(я) на выдачу)
            if (now - lastWarnTimestamp.getOrDefault(target.getUniqueId(), 0L) < cooldown) {
                sender.sendMessage(Component.text("Этому игроку недавно уже выдавали предупреждение!", NamedTextColor.GOLD));
                return true;
            }

            String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Не указано";

            // Обновляем данные
            int count = warns.merge(target.getUniqueId(), 1, Integer::sum);
            lastWarnTimestamp.put(target.getUniqueId(), now);

            // Вывод на экран и звук
            target.getScheduler().run(this, (task) -> {
                target.showTitle(Title.title(
                        Component.text("ПРЕДУПРЕЖДЕНИЕ (" + count + ")", NamedTextColor.RED),
                        Component.text("Причина: " + reason, NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
                ));
                target.playSound(target.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.8f);
            }, null);

            // Сообщение в локал чат в квадрате 100 на 100
            Component localMsg = (Component.text(sender.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" выдал предупреждение ", NamedTextColor.GRAY))
                    .append(Component.text(target.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" по причине: ", NamedTextColor.GRAY))
                    .append(Component.text(reason, NamedTextColor.YELLOW))
                    .append(Component.text(" (количество предупреждений игрока "+ target.getName() + ": ", NamedTextColor.GRAY))
                    .append(Component.text(count, NamedTextColor.RED))
                    .append(Component.text(")", NamedTextColor.GRAY));

            double radiusSq = 100.0 * 100.0;
            Location targetLoc = target.getLocation();
            for (Player p : target.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(targetLoc) <= radiusSq) {
                    p.sendMessage(localMsg);
                }
            }
            Bukkit.getConsoleSender().sendMessage(localMsg);

            // Механика сброса предов через пол часа
            target.getScheduler().runDelayed(this, (task) -> {
                // Прошло ли полчаса с последнего преда
                long lastTime = lastWarnTimestamp.getOrDefault(target.getUniqueId(), 0L);
                long halfHourInMillis = 30 * 60 * 1000;

                if (System.currentTimeMillis() - lastTime >= halfHourInMillis) {
                    warns.remove(target.getUniqueId());
                    lastWarnTimestamp.remove(target.getUniqueId());
                    target.sendMessage(Component.text("Ваши предупреждения были аннулированы, не буяньте. (прошло 30 минут)", NamedTextColor.GREEN));
                }
            }, null, 36000L); // 36000 = 30 минут

            return true;
        }

        return false;
    }
}