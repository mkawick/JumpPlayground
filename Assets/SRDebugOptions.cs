using SRDebugger;
using UnityEngine;
using KinematicCharacterControllerNamespace;

public class SRDebugOptions : MonoBehaviour
{
    bool _someInternalField;
    int _someInternalField2;
    private float _myRangeProperty = 0f;
    [SerializeField] TinyWizCharacterController TinyWizCharacterController;
    [SerializeField] KinematicCharacterConfig KinematicCharacterConfig;
    // Start is called once before the first execution of Update after the MonoBehaviour is created
    void Start()
    {

        var runSpeedOption = OptionDefinition.Create(
            "Run speed",
            () => TinyWizCharacterController.MaxStableMoveSpeed,
            (newValue) => TinyWizCharacterController.MaxStableMoveSpeed = newValue
        );
        var runAccelOption = OptionDefinition.Create(
            "Run speed accel",
            () => TinyWizCharacterController.StableMovementAcceleration,
            (newValue) => TinyWizCharacterController.StableMovementAcceleration = newValue
        );
        
        var jumpVertOption = OptionDefinition.Create(
            "Jump strength",
            () => TinyWizCharacterController.JumpUpSpeed,
            (newValue) => TinyWizCharacterController.JumpUpSpeed = newValue
        );
        var airSpeedOption = OptionDefinition.Create(
            "Air speed falling from ledge",
            () => TinyWizCharacterController.MaxAirMoveSpeed,
            (newValue) => TinyWizCharacterController.MaxAirMoveSpeed = newValue
        );

        var airAccelerationOption = OptionDefinition.Create(
            "Air control accel",
            () => TinyWizCharacterController.AirAccelerationSpeed,
            (newValue) => TinyWizCharacterController.AirAccelerationSpeed = newValue
        );

        var gravityOption = OptionDefinition.Create(
            "Gravity strength",
            () => TinyWizCharacterController.Gravity.y,
            (newValue) => TinyWizCharacterController.Gravity = new Vector3(0, newValue, 0)
        );

        var animRunSpeedOption = OptionDefinition.Create(
            "Anim run speed",
            () => KinematicCharacterConfig.animationRunSpeedFudgeFactor,
            (newValue) => KinematicCharacterConfig.animationRunSpeedFudgeFactor = newValue
        );

        var dashSpeedOption = OptionDefinition.Create(
            "Dash run speed",
            () => TinyWizCharacterController.DashSpeedMultiplier,
            (newValue) => TinyWizCharacterController.DashSpeedMultiplier = newValue
        );


        SRDebug.Instance.AddOption(runSpeedOption);
        SRDebug.Instance.AddOption(runAccelOption);
        SRDebug.Instance.AddOption(jumpVertOption);
        SRDebug.Instance.AddOption(airSpeedOption);
        SRDebug.Instance.AddOption(airAccelerationOption);
        SRDebug.Instance.AddOption(gravityOption);
        SRDebug.Instance.AddOption(animRunSpeedOption);
        SRDebug.Instance.AddOption(dashSpeedOption);
    }

    /*[NumberRange(0, 10)]
    [Category("My Category")]
    public float MyRangeProperty
    {
        get { return _myRangeProperty; }
        set { _myRangeProperty = value; }
    }*/

    // Update is called once per frame
    void Update()
    {
        
    }
}
