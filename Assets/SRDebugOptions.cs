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
        // Create a read-only option
        var option01 = OptionDefinition.Create("My Option", () => _someInternalField);
        SRDebug.Instance.AddOption(option01);

        var runSpeedOption = OptionDefinition.Create(
            "Run speed",
            () => TinyWizCharacterController.MaxStableMoveSpeed,
            (newValue) => TinyWizCharacterController.MaxStableMoveSpeed = newValue
        );
        var jumpVertOption = OptionDefinition.Create(
            "Jump strength",
            () => TinyWizCharacterController.JumpUpSpeed,
            (newValue) => TinyWizCharacterController.JumpUpSpeed = newValue
        );
        var airSpeedOption = OptionDefinition.Create(
            "Air speed",
            () => TinyWizCharacterController.MaxAirMoveSpeed,
            (newValue) => TinyWizCharacterController.MaxAirMoveSpeed = newValue
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
        SRDebug.Instance.AddOption(jumpVertOption);
        SRDebug.Instance.AddOption(airSpeedOption);
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
