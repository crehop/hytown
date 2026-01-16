package com.hytowny.gui;

import com.hytowny.HyTowny;
import com.hytowny.config.PluginConfig;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.Color;

/**
 * Admin GUI for managing HyTowny plugin settings.
 * Allows administrators to control costs, upkeep, wild protection, and other settings.
 */
public class TownAdminGui extends InteractiveCustomUIPage<TownAdminGui.AdminData> {

    private final HyTowny plugin;
    private final World world;

    private String inputValue = "";
    private String statusMessage = "";
    private boolean statusIsError = false;
    private String currentTab = "economy"; // economy, upkeep, wild, ranks

    private static final Color GREEN = new Color(85, 255, 85);
    private static final Color RED = new Color(255, 85, 85);
    private static final Color GOLD = new Color(255, 170, 0);

    public TownAdminGui(@Nonnull PlayerRef playerRef, HyTowny plugin, World world) {
        super(playerRef, CustomPageLifetime.CanDismiss, AdminData.CODEC);
        this.plugin = plugin;
        this.world = world;
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull AdminData data) {
        super.handleDataEvent(ref, store, data);

        var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            this.sendUpdate();
            return;
        }

        // Handle close button
        if (data.close != null) {
            this.close();
            return;
        }

        // Handle text input changes
        boolean textOnly = false;
        if (data.inputValue != null) {
            this.inputValue = data.inputValue;
            textOnly = true;
        }

        // If only text changed, don't rebuild
        if (textOnly && data.action == null && data.tab == null) {
            this.sendUpdate();
            return;
        }

        // Handle tab changes
        if (data.tab != null) {
            this.currentTab = data.tab;
            this.statusMessage = "";
            this.inputValue = "";
        }

        // Handle actions
        if (data.action != null) {
            handleAction(data.action, playerRef);
        }
    }

    private void handleAction(String action, PlayerRef playerRef) {
        PluginConfig config = plugin.getPluginConfig();

        switch (action) {
            // Economy Settings
            case "set_town_cost" -> {
                try {
                    double value = Double.parseDouble(inputValue);
                    config.setTownCreationCost(value);
                    statusMessage = "Town creation cost set to $" + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "set_claim_cost" -> {
                try {
                    double value = Double.parseDouble(inputValue);
                    config.setTownClaimCost(value);
                    statusMessage = "Claim cost set to $" + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "set_max_claims" -> {
                try {
                    int value = Integer.parseInt(inputValue);
                    config.setMaxTownClaims(value);
                    statusMessage = "Max town claims set to " + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }

            // Upkeep Settings
            case "set_upkeep_base" -> {
                try {
                    double value = Double.parseDouble(inputValue);
                    config.setTownUpkeepBase(value);
                    statusMessage = "Upkeep base set to $" + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "set_upkeep_per_plot" -> {
                try {
                    double value = Double.parseDouble(inputValue);
                    config.setTownUpkeepPerClaim(value);
                    statusMessage = "Upkeep per plot set to $" + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "set_upkeep_hour" -> {
                try {
                    int value = Integer.parseInt(inputValue);
                    if (value < 0 || value > 23) {
                        statusMessage = "Hour must be 0-23!";
                        statusIsError = true;
                    } else {
                        config.setTownUpkeepHour(value);
                        statusMessage = "Upkeep hour set to " + value + ":00";
                        statusIsError = false;
                    }
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }

            // Wild Protection Settings
            case "toggle_wild" -> {
                boolean current = config.isWildProtectionEnabled();
                config.setWildProtectionEnabled(!current);
                statusMessage = "Wild protection " + (!current ? "ENABLED" : "DISABLED");
                statusIsError = false;
            }
            case "set_wild_min_y" -> {
                try {
                    int value = Integer.parseInt(inputValue);
                    config.setWildProtectionMinY(value);
                    statusMessage = "Wild min Y set to " + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "toggle_wild_destroy" -> {
                boolean current = config.isWildDestroyBelowAllowed();
                config.setWildDestroyBelowAllowed(!current);
                statusMessage = "Destroy below Y: " + (!current ? "ALLOWED" : "BLOCKED");
                statusIsError = false;
            }
            case "toggle_wild_build" -> {
                boolean current = config.isWildBuildBelowAllowed();
                config.setWildBuildBelowAllowed(!current);
                statusMessage = "Build below Y: " + (!current ? "ALLOWED" : "BLOCKED");
                statusIsError = false;
            }

            // Personal Claims Settings
            case "set_starting_claims" -> {
                try {
                    int value = Integer.parseInt(inputValue);
                    config.setStartingClaims(value);
                    statusMessage = "Starting claims set to " + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "set_claims_per_hour" -> {
                try {
                    int value = Integer.parseInt(inputValue);
                    config.setClaimsPerHour(value);
                    statusMessage = "Claims per hour set to " + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "set_max_personal_claims" -> {
                try {
                    int value = Integer.parseInt(inputValue);
                    config.setMaxClaims(value);
                    statusMessage = "Max personal claims set to " + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }
            case "set_claim_buffer" -> {
                try {
                    int value = Integer.parseInt(inputValue);
                    config.setClaimBufferSize(value);
                    statusMessage = "Claim buffer set to " + value;
                    statusIsError = false;
                } catch (NumberFormatException e) {
                    statusMessage = "Invalid number!";
                    statusIsError = true;
                }
            }

            // Data Management
            case "reload" -> {
                config.reload();
                statusMessage = "Configuration reloaded!";
                statusIsError = false;
            }
            case "save_all" -> {
                plugin.getTownStorage().saveAll();
                plugin.getClaimStorage().saveAll();
                statusMessage = "All data saved!";
                statusIsError = false;
            }
            case "backup" -> {
                plugin.getTownStorage().createBackup();
                statusMessage = "Backup created!";
                statusIsError = false;
            }
        }

        inputValue = "";
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/hycrown_HyTowny_AdminMenu.ui");

        PluginConfig config = plugin.getPluginConfig();

        // Status message
        cmd.set("#StatusMessage.Text", statusMessage);
        cmd.set("#StatusMessage.Style.TextColor", statusIsError ? "#ff5555" : "#55ff55");

        // Title
        cmd.set("#Title.Text", "HyTowny Admin Panel");

        // Tab buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EconomyTab",
                EventData.of("Tab", "economy"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#UpkeepTab",
                EventData.of("Tab", "upkeep"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#WildTab",
                EventData.of("Tab", "wild"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PersonalTab",
                EventData.of("Tab", "personal"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DataTab",
                EventData.of("Tab", "data"), false);

        // Highlight current tab
        cmd.set("#EconomyTab.Style.BackgroundColor", currentTab.equals("economy") ? "#555555" : "#333333");
        cmd.set("#UpkeepTab.Style.BackgroundColor", currentTab.equals("upkeep") ? "#555555" : "#333333");
        cmd.set("#WildTab.Style.BackgroundColor", currentTab.equals("wild") ? "#555555" : "#333333");
        cmd.set("#PersonalTab.Style.BackgroundColor", currentTab.equals("personal") ? "#555555" : "#333333");
        cmd.set("#DataTab.Style.BackgroundColor", currentTab.equals("data") ? "#555555" : "#333333");

        // Input field
        cmd.set("#InputField.Value", inputValue);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#InputField",
                EventData.of("@InputValue", "#InputField.Value"), false);

        // Close button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Close", "true"), false);

        // Show/hide panels based on current tab
        cmd.set("#EconomyPanel.Visible", currentTab.equals("economy"));
        cmd.set("#UpkeepPanel.Visible", currentTab.equals("upkeep"));
        cmd.set("#WildPanel.Visible", currentTab.equals("wild"));
        cmd.set("#PersonalPanel.Visible", currentTab.equals("personal"));
        cmd.set("#DataPanel.Visible", currentTab.equals("data"));

        switch (currentTab) {
            case "economy" -> buildEconomyPanel(cmd, evt, config);
            case "upkeep" -> buildUpkeepPanel(cmd, evt, config);
            case "wild" -> buildWildPanel(cmd, evt, config);
            case "personal" -> buildPersonalPanel(cmd, evt, config);
            case "data" -> buildDataPanel(cmd, evt, config);
        }
    }

    private void buildEconomyPanel(UICommandBuilder cmd, UIEventBuilder evt, PluginConfig config) {
        cmd.set("#TownCostValue.Text", String.format("$%.0f", config.getTownCreationCost()));
        cmd.set("#ClaimCostValue.Text", String.format("$%.0f", config.getTownClaimCost()));
        cmd.set("#MaxClaimsValue.Text", String.valueOf(config.getMaxTownClaims()));

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetTownCostBtn",
                EventData.of("Action", "set_town_cost"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetClaimCostBtn",
                EventData.of("Action", "set_claim_cost"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetMaxClaimsBtn",
                EventData.of("Action", "set_max_claims"), false);
    }

    private void buildUpkeepPanel(UICommandBuilder cmd, UIEventBuilder evt, PluginConfig config) {
        cmd.set("#UpkeepBaseValue.Text", String.format("$%.0f/day", config.getTownUpkeepBase()));
        cmd.set("#UpkeepPerPlotValue.Text", String.format("$%.0f/plot/day", config.getTownUpkeepPerClaim()));
        cmd.set("#UpkeepHourValue.Text", config.getTownUpkeepHour() + ":00");

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetUpkeepBaseBtn",
                EventData.of("Action", "set_upkeep_base"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetUpkeepPerPlotBtn",
                EventData.of("Action", "set_upkeep_per_plot"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetUpkeepHourBtn",
                EventData.of("Action", "set_upkeep_hour"), false);
    }

    private void buildWildPanel(UICommandBuilder cmd, UIEventBuilder evt, PluginConfig config) {
        cmd.set("#WildEnabledValue.Text", config.isWildProtectionEnabled() ? "ENABLED" : "DISABLED");
        cmd.set("#WildMinYValue.Text", String.valueOf(config.getWildProtectionMinY()));
        cmd.set("#WildDestroyValue.Text", config.isWildDestroyBelowAllowed() ? "ALLOWED" : "BLOCKED");
        cmd.set("#WildBuildValue.Text", config.isWildBuildBelowAllowed() ? "ALLOWED" : "BLOCKED");

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleWildBtn",
                EventData.of("Action", "toggle_wild"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetWildMinYBtn",
                EventData.of("Action", "set_wild_min_y"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleDestroyBtn",
                EventData.of("Action", "toggle_wild_destroy"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleBuildBtn",
                EventData.of("Action", "toggle_wild_build"), false);
    }

    private void buildPersonalPanel(UICommandBuilder cmd, UIEventBuilder evt, PluginConfig config) {
        cmd.set("#StartingClaimsValue.Text", String.valueOf(config.getStartingClaims()));
        cmd.set("#ClaimsPerHourValue.Text", String.valueOf(config.getClaimsPerHour()));
        cmd.set("#MaxPersonalClaimsValue.Text", String.valueOf(config.getMaxClaims()));
        cmd.set("#ClaimBufferValue.Text", String.valueOf(config.getClaimBufferSize()));

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetStartingClaimsBtn",
                EventData.of("Action", "set_starting_claims"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetClaimsPerHourBtn",
                EventData.of("Action", "set_claims_per_hour"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetMaxPersonalClaimsBtn",
                EventData.of("Action", "set_max_personal_claims"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SetClaimBufferBtn",
                EventData.of("Action", "set_claim_buffer"), false);
    }

    private void buildDataPanel(UICommandBuilder cmd, UIEventBuilder evt, PluginConfig config) {
        int townCount = plugin.getTownStorage().getTownCount();
        cmd.set("#TownCountValue.Text", String.valueOf(townCount));

        var backups = plugin.getTownStorage().listBackups();
        cmd.set("#BackupCountValue.Text", backups.size() + " backups");

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ReloadBtn",
                EventData.of("Action", "reload"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SaveAllBtn",
                EventData.of("Action", "save_all"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackupBtn",
                EventData.of("Action", "backup"), false);
    }

    /**
     * Open the admin GUI for a player.
     */
    public static void openFor(HyTowny plugin, Player player, PlayerRef playerRef, World world) {
        TownAdminGui gui = new TownAdminGui(playerRef, plugin, world);
        player.getPageManager().pushPage(gui);
    }

    /**
     * Data class for receiving UI events.
     */
    public static class AdminData {
        public String close;
        public String tab;
        public String action;
        public String inputValue;

        public static final Codec<AdminData> CODEC = BuilderCodec.of(AdminData::new,
                KeyedCodec.optional("Close", Codec.STRING, d -> d.close, (d, v) -> d.close = v),
                KeyedCodec.optional("Tab", Codec.STRING, d -> d.tab, (d, v) -> d.tab = v),
                KeyedCodec.optional("Action", Codec.STRING, d -> d.action, (d, v) -> d.action = v),
                KeyedCodec.optional("@InputValue", Codec.STRING, d -> d.inputValue, (d, v) -> d.inputValue = v)
        );
    }
}
