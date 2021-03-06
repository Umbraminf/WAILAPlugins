package tterrag.wailaplugins.plugins;

import java.text.DecimalFormat;
import java.util.List;

import com.enderio.core.common.util.ItemUtil;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaEntityAccessor;
import mcp.mobius.waila.api.IWailaEntityProvider;
import mcp.mobius.waila.api.IWailaRegistrar;
import mcp.mobius.waila.api.impl.ModuleRegistrar;
import mods.railcraft.common.blocks.charge.CapabilityCartBattery;
import mods.railcraft.common.blocks.charge.ChargeManager;
import mods.railcraft.common.blocks.charge.IChargeBlock;
import mods.railcraft.common.blocks.machine.TileMachineBase;
import mods.railcraft.common.blocks.machine.TileMultiBlock;
import mods.railcraft.common.blocks.machine.beta.TileBoilerFirebox;
import mods.railcraft.common.blocks.machine.beta.TileBoilerTank;
import mods.railcraft.common.blocks.machine.beta.TileEngine;
import mods.railcraft.common.blocks.machine.beta.TileEngineSteam;
import mods.railcraft.common.blocks.machine.beta.TileEngineSteamHobby;
import mods.railcraft.common.blocks.machine.beta.TileTankBase;
import mods.railcraft.common.carts.EntityLocomotive;
import mods.railcraft.common.carts.EntityLocomotiveElectric;
import mods.railcraft.common.carts.EntityLocomotiveSteam;
import mods.railcraft.common.fluids.tanks.StandardTank;
import mods.railcraft.common.items.RailcraftItems;
import mods.railcraft.common.plugins.buildcraft.triggers.ITemperature;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.capability.FluidTankPropertiesWrapper;
import net.minecraftforge.fluids.capability.IFluidHandler;
import tterrag.wailaplugins.api.Plugin;
import tterrag.wailaplugins.config.WPConfigHandler;

@Plugin(name = "Railcraft", deps = "railcraft")
public class PluginRailcraft extends PluginBase implements IWailaEntityProvider
{
    private static final DecimalFormat fmtCharge = new DecimalFormat("#.##");
    @Override
    public void load(IWailaRegistrar registrar)
    {
        super.load(registrar);
        
        registerBody(TileMachineBase.class, IChargeBlock.class);
        registerNBT(TileEngineSteam.class, IChargeBlock.class, TileMultiBlock.class);

        registerEntityBody(this, EntityLocomotive.class);        
        registerEntityNBT(this, EntityLocomotive.class);
        
        addConfig("multiblocks");
        addConfig("heat");
        addConfig("tanks");
        addConfig("energy");
        addConfig("engines");
        addConfig("charge");
        addConfig("locomotives");
    }

    @Override
    public void postLoad()
    {
        // Remove WAILA's RC plugin
        ModuleRegistrar.instance().bodyBlockProviders.remove(TileTankBase.class);
    }

    @Override
    protected void getBody(ItemStack stack, List<String> currenttip, IWailaDataAccessor accessor)
    {
        TileEntity tile = accessor.getTileEntity();
        NBTTagCompound tag = accessor.getNBTData();

        if (tile instanceof TileMultiBlock && getConfig("multiblocks"))
        {
            currenttip.add(String.format(lang.localize("formed"), lang.localize(((TileMultiBlock)tile).isStructureValid() ? "yes" : "no")));
        }

        if ((tile instanceof TileEngineSteamHobby || tile instanceof TileBoilerFirebox || tile instanceof TileBoilerTank) && getConfig("heat"))
        {
            addHeatTooltip(currenttip, tag);
        }

        if (tile instanceof TileEngine && getConfig("engines"))
        {
            int energy  = tag.getInteger(ENERGY_STORED);
            int gen     = Math.round(tag.getFloat(CURRENT_OUTPUT));
            
            currenttip.add(lang.localize("energyStored", energy + " / " + ((TileEngine)tile).maxEnergy() + " RF"));
            currenttip.add(lang.localize("generating", gen));
        }
        
        if (tag.hasKey(TANK_FLUID))
        {
            FluidTankInfo info = PluginIFluidHandler.readFluidInfoFromNBT(tag.getCompoundTag(TANK_FLUID));
            PluginIFluidHandler.addTankTooltip(currenttip, info);
        }
        
        if (getConfig("charge") && tag.hasKey(CHARGE))
        {
            addChargeTooltip(currenttip, tag, accessor.getPlayer());
        }
    }
    
    private static void addChargeTooltip(List<String> currenttip, NBTTagCompound tag, EntityPlayer player)
    {
        ItemStack current = player.getHeldItemMainhand();
        boolean hasMeter = !WPConfigHandler.meterInHand || (current != null && ItemUtil.stacksEqual(current, RailcraftItems.CHARGE_METER.getStack()));
        
        double charge = tag.getDouble(CHARGE);
        String chargeFmt = fmtCharge.format(charge) + "c";
        
        currenttip.add(TextFormatting.RESET + String.format(lang.localize("charge"), hasMeter ? chargeFmt : (TextFormatting.ITALIC + lang.localize("needMeter"))));
    }
    
    private static void addHeatTooltip(List<String> currenttip, NBTTagCompound tag)
    {
        int heat = Math.round(tag.getFloat(HEAT));
        int max  = Math.round(tag.getFloat(MAX_HEAT));
                
        currenttip.add(String.format(lang.localize("engineTemp"), heat, max));
    }
    
    public static final String TANK_FLUID = "tankFluid";
    public static final String HEAT = "heat";
    public static final String MAX_HEAT = "maxHeat";
    public static final String CURRENT_OUTPUT = "currentOutput";
    public static final String ENERGY_STORED = "energyStored";
    public static final String CHARGE = "charge";
    
    @Override
    protected void getNBTData(TileEntity te, NBTTagCompound tag, World world, BlockPos pos)
    {
        if (te instanceof TileMultiBlock && ((TileMultiBlock) te).getMasterBlock() != null)
        {
            if (te instanceof TileTankBase && !(te instanceof IFluidHandler))
            {
                te = ((TileMultiBlock) te).getMasterBlock();
                StandardTank tank = ((TileTankBase)te).getTank();
                NBTTagCompound fluidTag = new NBTTagCompound();
                PluginIFluidHandler.writeFluidInfoToNBT(new FluidTankPropertiesWrapper(tank), fluidTag);
                tag.setTag(TANK_FLUID, fluidTag);
            }
            te = ((TileMultiBlock) te).getMasterBlock();
        }
        if (te instanceof ITemperature)
        {
            tag.setFloat(HEAT, ((ITemperature) te).getTemperature());
        }
        if (te instanceof TileEngine)
        {
            tag.setFloat(CURRENT_OUTPUT, ((TileEngine) te).currentOutput);
            tag.setInteger(ENERGY_STORED, ((TileEngine) te).getEnergy());
            if (te instanceof TileEngineSteamHobby)
            {
                tag.setDouble(MAX_HEAT, ((TileEngineSteamHobby) te).boiler.getMaxHeat());
            }
        }
        // XXX WAILA does not call this on TE-less blocks, so shunting wire does not work atm
        if (te.getBlockType() instanceof IChargeBlock)
        {
            tag.setDouble(CHARGE, ChargeManager.getNetwork(world).getNode(pos).getBattery().getCharge());
        }
    }

    @Override
    public Entity getWailaOverride(IWailaEntityAccessor accessor, IWailaConfigHandler config)
    {
        return null;
    }

    @Override
    public List<String> getWailaHead(Entity entity, List<String> currenttip, IWailaEntityAccessor accessor, IWailaConfigHandler config)
    {
        return null;
    }

    @Override
    public List<String> getWailaBody(Entity entity, List<String> currenttip, IWailaEntityAccessor accessor, IWailaConfigHandler config)
    {
        NBTTagCompound tag = accessor.getNBTData();
        
        if (!getConfig("locomotives"))
        {
            return currenttip;
        }
        
        if (entity instanceof EntityLocomotiveElectric)
        {
            addChargeTooltip(currenttip, tag, accessor.getPlayer());
        }
        
        if (entity instanceof EntityLocomotiveSteam)
        {
            addHeatTooltip(currenttip, tag);
        }
        
        return currenttip;
    }

    @Override
    public List<String> getWailaTail(Entity entity, List<String> currenttip, IWailaEntityAccessor accessor, IWailaConfigHandler config)
    {
        return null;
    }

    @Override
    public  NBTTagCompound getNBTData(EntityPlayerMP player, Entity ent, NBTTagCompound tag, World world)
    {
        if (ent instanceof EntityLocomotiveElectric)
        {
            tag.setDouble(CHARGE, ent.getCapability(CapabilityCartBattery.CHARGE_CART_CAPABILITY, null).getCharge());
        }
        
        if (ent instanceof EntityLocomotiveSteam)
        {
            tag.setDouble(HEAT, ((EntityLocomotiveSteam) ent).boiler.getHeat());
            tag.setDouble(MAX_HEAT, ((EntityLocomotiveSteam) ent).boiler.getMaxHeat());
        }
        
        return tag;
     }
}
