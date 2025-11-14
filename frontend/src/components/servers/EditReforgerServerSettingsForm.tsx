import {useFormik} from "formik";
import {AutocompleteValue, FormGroup, Grid, Typography, Divider, Chip, Box, TextField} from "@mui/material";
import {ReforgerServerDto} from "../../dtos/ServerDto.ts";
import {ReforgerScenarioDto} from "../../dtos/ReforgerScenarioDto.ts";
import {ReforgerScenariosAutocomplete} from "./ReforgerScenariosAutocomplete.tsx";
import {SwitchField} from "../../UI/Form/SwitchField.tsx";
import {CustomTextField} from "../../UI/Form/CustomTextField.tsx";
import {ServerSettingsFormControls} from "./ServerSettingsFormControls.tsx";
import {CustomLaunchParametersInput} from "./CustomLaunchParametersInput.tsx";
import {useState} from "react";

type EditReforgerServerSettingsFormProps = {
    server: ReforgerServerDto,
    isServerRunning?: boolean,
    onSubmit: (values: ReforgerServerDto) => void,
    onCancel: () => void
}

export default function EditReforgerServerSettingsForm(props: EditReforgerServerSettingsFormProps) {

    const [launchParameters, setLaunchParameters] = useState([...props.server.customLaunchParameters]);
    const [admins, setAdmins] = useState<string[]>(props.server.admins || []);
    const [adminInput, setAdminInput] = useState('');
    const [platforms, setPlatforms] = useState<string[]>(props.server.supportedPlatforms || ['PLATFORM_PC']);

    function handleSubmit(values: ReforgerServerDto) {
        values.customLaunchParameters = [...launchParameters];
        values.admins = [...admins];
        values.supportedPlatforms = [...platforms];
        props.onSubmit(values);
    }

    const formik = useFormik<ReforgerServerDto>({
        initialValues: props.server,
        onSubmit: handleSubmit,
        enableReinitialize: true
    });

    const setScenario = (_: any, value: AutocompleteValue<ReforgerScenarioDto | string, false, false, true> | null): void => {
        console.log(_, value);
        if (!value) {
            return;
        }
        formik.setFieldValue("scenarioId", typeof value === "string" ? value : value.value);
    };

    const handleAddAdmin = () => {
        if (adminInput && !admins.includes(adminInput)) {
            setAdmins([...admins, adminInput]);
            setAdminInput('');
        }
    };

    const handleDeleteAdmin = (adminToDelete: string) => {
        setAdmins(admins.filter(admin => admin !== adminToDelete));
    };

    const togglePlatform = (platform: string) => {
        if (platforms.includes(platform)) {
            setPlatforms(platforms.filter(p => p !== platform));
        } else {
            setPlatforms([...platforms, platform]);
        }
    };

    return (
        <div>
            <form onSubmit={formik.handleSubmit}>
                <Grid container spacing={3}>
                    <CustomTextField id='name' label='Server name' required formik={formik}/>
                    <CustomTextField id='description' label='Description' formik={formik}/>
                    <CustomTextField id='port' label='Port' type='number' formik={formik}/>
                    <CustomTextField id='queryPort' label='Query port' type='number' formik={formik}/>
                    <Grid item xs={12} md={6}>
                        <ReforgerScenariosAutocomplete onChange={setScenario} formik={formik}/>
                    </Grid>
                    <CustomTextField id='maxPlayers' label='Max players' required type='number' formik={formik}/>
                    <CustomTextField id='password' label='Password' formik={formik}/>
                    <CustomTextField id='adminPassword' label='Admin password' required formik={formik}/>
                    
                    <Grid item xs={12}>
                        <Divider sx={{my: 2}}><Typography variant="h6">Server Settings</Typography></Divider>
                    </Grid>
                    
                    <Grid item xs={12}>
                        <FormGroup>
                            <SwitchField id='battlEye' label='BattlEye enabled' formik={formik}/>
                            <SwitchField id='thirdPersonViewEnabled' label='Third person view enabled' formik={formik}/>
                            <SwitchField id='crossPlatform' label='Cross-platform play enabled (PC/Xbox/PlayStation)' formik={formik}/>
                            <SwitchField id='fastValidation' label='Fast validation enabled' formik={formik}/>
                        </FormGroup>
                    </Grid>
                    
                    <Grid item xs={12}>
                        <Divider sx={{my: 2}}><Typography variant="h6">Supported Platforms</Typography></Divider>
                    </Grid>
                    
                    <Grid item xs={12}>
                        <Box sx={{display: 'flex', gap: 1, flexWrap: 'wrap'}}>
                            <Chip 
                                label="PC" 
                                color={platforms.includes('PLATFORM_PC') ? 'primary' : 'default'}
                                onClick={() => togglePlatform('PLATFORM_PC')}
                                clickable
                            />
                            <Chip 
                                label="Xbox" 
                                color={platforms.includes('PLATFORM_XBL') ? 'primary' : 'default'}
                                onClick={() => togglePlatform('PLATFORM_XBL')}
                                clickable
                            />
                            <Chip 
                                label="PlayStation" 
                                color={platforms.includes('PLATFORM_PSN') ? 'primary' : 'default'}
                                onClick={() => togglePlatform('PLATFORM_PSN')}
                                clickable
                            />
                        </Box>
                    </Grid>
                    
                    <Grid item xs={12}>
                        <Divider sx={{my: 2}}><Typography variant="h6">View Distance Settings</Typography></Divider>
                    </Grid>
                    
                    <CustomTextField id='serverMaxViewDistance' label='Server max view distance (m)' type='number' formik={formik}/>
                    <CustomTextField id='networkViewDistance' label='Network view distance (m)' type='number' formik={formik}/>
                    <CustomTextField id='serverMinGrassDistance' label='Server min grass distance (m)' type='number' formik={formik}/>
                    
                    <Grid item xs={12}>
                        <Divider sx={{my: 2}}><Typography variant="h6">Server Admins (Steam64 IDs)</Typography></Divider>
                    </Grid>
                    
                    <Grid item xs={12}>
                        <Box sx={{display: 'flex', gap: 1, mb: 1}}>
                            <TextField
                                label="Steam64 ID"
                                variant="outlined"
                                size="small"
                                value={adminInput}
                                onChange={(e) => setAdminInput(e.target.value)}
                                onKeyPress={(e) => e.key === 'Enter' && (e.preventDefault(), handleAddAdmin())}
                                fullWidth
                            />
                        </Box>
                        <Box sx={{display: 'flex', gap: 1, flexWrap: 'wrap'}}>
                            {admins.map((admin) => (
                                <Chip
                                    key={admin}
                                    label={admin}
                                    onDelete={() => handleDeleteAdmin(admin)}
                                    color="primary"
                                />
                            ))}
                        </Box>
                    </Grid>
                    
                    <Grid item xs={12}>
                        <Divider sx={{my: 2}}><Typography variant="h6">Advanced Launch Parameters</Typography></Divider>
                    </Grid>
                    
                    <Grid item xs={12}>
                        <CustomLaunchParametersInput
                            valueDelimiter=' '
                            parameters={launchParameters}
                            onParametersChange={setLaunchParameters}
                        />
                    </Grid>
                    <ServerSettingsFormControls serverRunning={props.isServerRunning} onCancel={props.onCancel}/>
                </Grid>
            </form>
        </div>
    );
}
