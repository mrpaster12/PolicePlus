# Handcuff Plugin Documentation

## Overview
The Handcuff Plugin adds handcuff functionality to the Police Plugin, allowing police officers to cuff players and transport them to jail.

## Features
- **Physical Handcuff Items**: Use handcuff items to cuff players
- **Command-based Cuffing**: Use commands to cuff/uncuff players
- **Automatic Jail Integration**: Cuffed players can be sent to jail
- **Permission System**: Different permissions for different handcuff actions
- **Configurable Items**: Customize handcuff item type, name, and lore
- **Timeout System**: Handcuffs automatically expire after configurable time

## Commands

### `/cuffe <player>`
Cuffs a player using the handcuff item in your hand.
- **Permission**: `policeplugin.handcuff.cuff`
- **Usage**: Right-click with handcuff item near a player

### `/cuffe give <player> [amount]`
Gives handcuff items to a player.
- **Permission**: `policeplugin.handcuff.give`
- **Amount**: Optional, defaults to 1, max 64

### `/uncuffe <player>`
Removes handcuffs from a player.
- **Permission**: `policeplugin.handcuff.uncuff`
- **Note**: You can only uncuff players you cuffed, unless you have `policeplugin.handcuff.uncuff_any`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `policeplugin.handcuff.cuff` | Can cuff players with handcuffs | op |
| `policeplugin.handcuff.uncuff` | Can uncuff players | op |
| `policeplugin.handcuff.give` | Can give handcuff items | op |
| `policeplugin.handcuff.uncuff_any` | Can uncuff any player | op |

## Configuration

### config.yml
```yaml
handcuff:
  item: "IRON_INGOT"           # Item type for handcuffs
  name: "&cدستبند"             # Display name for handcuff item
  lore: "&7برای دستگیر کردن بازیکنان"  # Lore for handcuff item
  max_time: 300                # Maximum time player can be cuffed (seconds)
```

### Language Files
The plugin supports both English and Persian languages with all handcuff-related messages.

## How It Works

### Cuffing Process
1. **Physical Cuffing**: Right-click with handcuff item near a player (within 3 blocks)
2. **Command Cuffing**: Use `/cuffe <player>` command
3. **Effects Applied**: 
   - Player's walk speed reduced to 0
   - Player's fly speed reduced to 0
   - Player cannot move far from the officer who cuffed them

### Jail Integration
- Cuffed players can be sent to jail using the existing jail system
- When sent to jail, handcuffs are automatically removed
- Uses the same arrest system as the main police plugin

### Timeout System
- Handcuffs automatically expire after the configured time
- Players are notified when handcuffs timeout
- All cuff effects are removed automatically

## Usage Examples

### For Police Officers
1. Get handcuff items: `/cuffe give <yourname> 5`
2. Cuff a player: Right-click near them with handcuff item
3. Transport to jail: Use existing jail commands
4. Uncuff when needed: `/uncuffe <player>`

### For Administrators
1. Give handcuffs to officers: `/cuffe give <officer> 10`
2. Monitor cuffed players through the plugin
3. Configure handcuff settings in config.yml

## Integration with Police Plugin

The handcuff system integrates seamlessly with the existing police plugin:
- Uses the same permission system
- Integrates with the jail system
- Follows the same language system
- Compatible with all existing police features

## Technical Details

### Data Storage
- Cuffed players are stored in memory
- Data persists during server runtime
- Can be extended to persist across restarts

### Performance
- Minimal impact on server performance
- Efficient timeout checking (every second)
- Optimized player movement monitoring

### Compatibility
- Compatible with Spigot 1.17+
- Works with Paper, Bukkit, and other server types
- Integrates with PlaceholderAPI if available

## Troubleshooting

### Common Issues
1. **Handcuffs not working**: Check permissions and item configuration
2. **Players can't be cuffed**: Verify they're not already cuffed
3. **Timeout not working**: Check the `max_time` configuration value

### Debug Commands
- Check if a player is cuffed: Look for reduced movement speed
- Verify handcuff items: Check item type and name in config
- Test permissions: Use permission checking plugins

## Support

For issues or questions:
1. Check the configuration file
2. Verify permissions are set correctly
3. Check server logs for error messages
4. Ensure all dependencies are properly installed

## Version History

- **1.0.0**: Initial release with basic handcuff functionality
- Features: Physical items, commands, jail integration, timeout system
