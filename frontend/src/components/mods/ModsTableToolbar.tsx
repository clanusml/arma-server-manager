import Tooltip from "@mui/material/Tooltip";
import IconButton from "@mui/material/IconButton";
import DeleteIcon from "@mui/icons-material/Delete";
import {Box, Tab, Tabs} from "@mui/material";
import UpdateIcon from '@mui/icons-material/Update';
import PlaylistAddIcon from '@mui/icons-material/PlaylistAdd';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';

type ModsTableToolbarProps = {
    selectedModsCount: number,
    filter: string
    arma3ModsCount: number
    dayZModsCount: number
    mixedModsSelected: boolean
    onUpdateClicked: () => void,
    onCreatePresetClicked: () => void,
    onUninstallClicked: () => void,
    onClearCacheClicked: () => void,
    onFilterChange: (_: any, newValue: string) => void,
}

function ModsTableToolbar(
    {
        selectedModsCount,
        filter,
        onFilterChange,
        onUpdateClicked,
        arma3ModsCount,
        dayZModsCount,
        onCreatePresetClicked,
        onUninstallClicked,
        onClearCacheClicked,
        mixedModsSelected
    }: ModsTableToolbarProps)
{
    return (
        <>
            <Box>
                <Tabs value={filter} onChange={onFilterChange}>
                    <Tab value="" label="All"/>
                    <Tab value="ARMA3" label="Arma 3" disabled={arma3ModsCount === 0}/>
                    <Tab value="DAYZ" label="DayZ" disabled={dayZModsCount === 0}/>
                </Tabs>
            </Box>

            <Tooltip title="Update">
                        <span>
                            <IconButton disabled={selectedModsCount === 0} onClick={onUpdateClicked}>
                                <UpdateIcon/>
                            </IconButton>
                        </span>
            </Tooltip>
            <Tooltip title="Save as preset">
                        <span>
                            <IconButton disabled={selectedModsCount === 0 || mixedModsSelected}
                                        onClick={onCreatePresetClicked}>
                                <PlaylistAddIcon/>
                            </IconButton>
                        </span>
            </Tooltip>
            <Tooltip title="Delete">
                        <span>
                            <IconButton disabled={selectedModsCount === 0} onClick={onUninstallClicked}>
                                <DeleteIcon/>
                            </IconButton>
                        </span>
            </Tooltip>
            <Tooltip title="Clear Steam cache">
                        <span>
                            <IconButton onClick={onClearCacheClicked}>
                                <DeleteSweepIcon/>
                            </IconButton>
                        </span>
            </Tooltip>
        </>
    );
}

export default ModsTableToolbar;